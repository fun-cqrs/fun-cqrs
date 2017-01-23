package io.funcqrs.akka

import java.util.concurrent.TimeoutException

import akka.actor._
import akka.pattern._
import akka.persistence.query.EventEnvelope
import akka.stream.ActorMaterializer
import akka.stream.actor.ActorSubscriberMessage.{ OnError, OnNext }
import akka.stream.actor.{ ActorSubscriber, RequestStrategy, WatermarkRequestStrategy }
import akka.stream.scaladsl.Sink
import akka.util.Timeout
import io.funcqrs.akka.util.ConfigReader.projectionConfig
import io.funcqrs.config.CustomOffsetPersistenceStrategy
import io.funcqrs.projections.{ Envelope, Projection }

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

abstract class ProjectionActor(
    projection: Projection,
    sourceProvider: EventsSourceProvider
) extends Actor
    with ActorLogging
    with ActorSubscriber
    with Stash {

  import context.dispatcher

  implicit val timeout = Timeout(5 seconds)

  var lastProcessedOffset: Option[Long] = None

  private var stashSize                       = 0
  private var stillToProcessMessageCount: Int = 0
  def isStashing: Boolean = stashSize > 0

  val maxDemand                        = 10
  private val watermarkRequestStrategy = WatermarkRequestStrategy(maxDemand)

  private val projectionTimeout =
    projectionConfig(projection.name).getDuration("async-projection-timeout", 5.seconds)

  protected def requestStrategy: RequestStrategy = new RequestStrategy {
    def requestDemand(remainingRequested: Int): Int = {
      if (isStashing) {
        0
      } else {
        watermarkRequestStrategy.requestDemand(stillToProcessMessageCount)
      }
    }
  }

  def stashWithBackPressure(): Unit = {
    stash()
    stashSize += 1
  }

  def unstashAllWithBackPressure(): Unit = {
    stillToProcessMessageCount = stashSize
    super.unstashAll()
    stashSize = 0
  }

  def saveCurrentOffset(offset: Long): Future[Unit]

  // default request strategy
  protected def fallbackRequestStrategy: RequestStrategy = WatermarkRequestStrategy(200)

  def recoveryCompleted(): Unit = {
    log.debug("ProjectionActor: starting projection... {}", projection)
    implicit val mat = ActorMaterializer()

    val subscriber = ActorSubscriber[EventEnvelope](self)
    val actorSink  = Sink.fromSubscriber(subscriber)

    sourceProvider.source(lastProcessedOffset.map(_ + 1).getOrElse(0)).runWith(actorSink)
  }

  override def receive: Receive = acceptingEvents

  def runningProjection(currentEvent: Any, offset: Long): Receive = {

    val receive: Receive = {

      // stash new events while busy with projection
      case OnNextEvent(_, _) => stashWithBackPressure()

      // ready with projection, notify parent and start consuming events
      case ProjectionActor.Done(lastEvent) =>
        log.debug("Processed {}, sending to parent {}", lastEvent, context.parent)
        context.parent ! lastEvent // send last processed event to parent

        // first switch behavior
        // when used in combination with PersistedOffsetAkka we'll switch twice
        // when PersistedOffsetAkka is ready, we must become waitingOffsetPersistence back
        // via unbecome call in PersistedOffsetAkka. Uff! Complicated!
        context become waitingOffsetPersistence

        // save offset of last processed event
        // event will be processed twice if saveCurrentOffset fails
        // therefore Projections should be idempotent or fail-safe
        saveCurrentOffset(offset)
          .map { _ =>
            ProjectionActor.OffsetPersisted(offset)
          }
          .pipeTo(self)

      case OnNext(any) =>
        log.warning("Received something that is not a DomainEvent! {} - [{}]", any, self.path.name)
    }

    receive orElse errorHandling("running projection")
  }

  def acceptingEvents: Receive = {

    val receive: Receive = {
      case OnNextEvent(evt, offset) =>
        log.debug("Received event {}", evt)

        val eventualTimeout =
          after(duration = projectionTimeout, using = context.system.scheduler) {
            Future.failed(new TimeoutException(s"Timed out projection ${projection.name} for event $evt"))
          }

        val projectionFuture = projection.onEvent(Envelope(evt, offset))

        val projectionWithTimeout = Future firstCompletedOf Seq(projectionFuture, eventualTimeout)

        projectionWithTimeout.map(_ => ProjectionActor.Done(evt)).pipeTo(self)

        projectionWithTimeout.onFailure {
          case e =>
            log.warning(s"Error while running projection for event $evt in projection ${projection.name}")
            log.error(e, e.getMessage)
        }

        context become runningProjection(evt, offset)

      case OnNext(any) => log.warning("Receive something that is not a DomainEvent! {}", any)
    }

    receive orElse errorHandling("accepting events")
  }

  /**
    * Used as error handling Receive when running projections or accepting new events.
    *
    * Will stop the actor whenever a failure kicks in. BackoffSupervisor must restart it
    */
  def errorHandling(phase: String): Receive = {
    case OnError(e) => // receive an error from the stream
      log.error(e, "OnError while {} ... [{}]", phase, self.path.name)
      context.stop(self)

    case Status.Failure(e) => // receive a general error, probably from the projection
      log.error(e, "Failure while {} ... [{}]", phase, self.path.name)
      context.stop(self)
  }

  def waitingOffsetPersistence: Receive = {

    case ProjectionActor.OffsetPersisted(offset) =>
      unstashAllWithBackPressure()
      lastProcessedOffset = Option(offset)
      context become acceptingEvents

    case Status.Failure(e) =>
      // continue processing anyway,
      // we can only hope that next time we'll be able to save the offset
      unstashAllWithBackPressure()
      context become acceptingEvents

    case anyOther => stashWithBackPressure()
  }

  object OnNextEvent {

    def unapply(onNext: OnNext): Option[(Any, Long)] = {
      onNext.element match {
        case EventEnvelope(offset, _, _, event) => Option((event, offset))
        case _                                  => None
      }
    }
  }

}

object ProjectionActor {

  case class Done(evt: Any)
  case class OffsetPersisted(offset: Long)

}

/**
  * A ProjectionActor that never saves the offset
  * causing the event stream to be read from start on each app restart
  */
class ProjectionActorWithoutOffsetPersistence(
    projection: Projection,
    sourceProvider: EventsSourceProvider
) extends ProjectionActor(projection, sourceProvider)
    with OffsetNotPersisted

object ProjectionActorWithoutOffsetPersistence {

  def props(
      projection: Projection,
      sourceProvider: EventsSourceProvider
  ): Props = {

    Props(new ProjectionActorWithoutOffsetPersistence(projection, sourceProvider))
  }

}

/**
  * A ProjectionActor that saves the offset as a snapshot in Akka Persistence
  *
  * This implementation is a quick win for those that simply want to persist the offset without caring about
  * the persistence layer.
  *
  * However, the drawback is that most (if not all) akka-persistence snapshot plugins will
  * save it as binary data which make it difficult to inspect the DB to get to know the last processed event.
  */
class ProjectionActorWithOffsetManagedByAkkaPersistence(
    projection: Projection,
    sourceProvider: EventsSourceProvider,
    val persistenceId: String
) extends ProjectionActor(projection, sourceProvider)
    with PersistedOffsetAkka

object ProjectionActorWithOffsetManagedByAkkaPersistence {

  def props(
      projection: Projection,
      sourceProvider: EventsSourceProvider,
      persistenceId: String
  ): Props = {

    Props(new ProjectionActorWithOffsetManagedByAkkaPersistence(projection, sourceProvider, persistenceId))
  }
}

/** A ProjectionActor that saves the offset using a [[CustomOffsetPersistenceStrategy]] */
class ProjectionActorWithCustomOffsetPersistence[E](
    projection: Projection,
    sourceProvider: EventsSourceProvider,
    customOffsetPersistence: CustomOffsetPersistenceStrategy
) extends ProjectionActor(projection, sourceProvider)
    with PersistedOffsetCustom {

  def saveCurrentOffset(offset: Long): Future[Unit] = {
    customOffsetPersistence.saveCurrentOffset(offset)
  }

  /** Returns the current offset as persisted in DB */
  def readOffset: Future[Option[Long]] = customOffsetPersistence.readOffset
}

object ProjectionActorWithCustomOffsetPersistence {

  def props(
      projection: Projection,
      sourceProvider: EventsSourceProvider,
      customOffsetPersistence: CustomOffsetPersistenceStrategy
  ): Props = {

    Props(new ProjectionActorWithCustomOffsetPersistence(projection, sourceProvider, customOffsetPersistence))
  }

}
