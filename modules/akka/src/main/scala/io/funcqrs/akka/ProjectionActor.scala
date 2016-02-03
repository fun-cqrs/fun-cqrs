package io.funcqrs.akka

import akka.actor._
import akka.pattern._
import akka.persistence.query.EventEnvelope
import akka.stream.ActorMaterializer
import akka.stream.actor.ActorSubscriberMessage.{ OnError, OnNext }
import akka.stream.actor.{ ActorSubscriber, RequestStrategy, WatermarkRequestStrategy }
import akka.stream.scaladsl.Sink
import akka.util.Timeout
import io.funcqrs.config.CustomOffsetPersistenceStrategy
import io.funcqrs.{ DomainEvent, Projection }

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }
import scala.language.postfixOps
import scala.util.control.NonFatal

// TODO: document each parameter
abstract class ProjectionActor(
    projection: Projection,
    sourceProvider: EventsSourceProvider
) extends Actor with ActorLogging with Stash {

  import context.dispatcher

  implicit val timeout = Timeout(5 seconds)

  var lastProcessedOffset: Option[Long] = None

  def saveCurrentOffset(offset: Long): Future[Unit]

  def recoveryCompleted(): Unit = {
    log.debug(s"ProjectionActor: starting projection... $projection")
    implicit val mat = ActorMaterializer()
    val actorSink = Sink.actorSubscriber(Props(classOf[ForwardingActorSubscriber], self, WatermarkRequestStrategy(10)))
    sourceProvider.source(lastProcessedOffset.map(_ + 1).getOrElse(0)).runWith(actorSink)
  }

  override def receive: Receive = acceptingEvents

  def runningProjection(currentEvent: DomainEvent, offset: Long): Receive = {

    val receive: Receive = {

      // stash new events while busy with projection
      case OnNextDomainEvent(_, _) => stash()

      // ready with projection, notify parent and start consuming events
      case ProjectionActor.Done(lastEvent) =>
        log.debug(s"Processed $lastEvent, sending to parent ${context.parent}")
        context.parent ! lastEvent // send last processed event to parent

        // first switch behavior
        // when used in combination with PersistedOffsetAkka we'll switch twice
        // when PersistedOffsetAkka is ready, we must become waitingOffsetPersistence back
        // via unbecome call in PersistedOffsetAkka. Uff! Complicated!
        context become waitingOffsetPersistence(lastEvent)

        // save offset of last processed event
        // event will be processed twice if saveCurrentOffset fails
        // therefore Projections should be idempotent or fail-safe
        saveCurrentOffset(offset).map { _ =>
          ProjectionActor.OffsetPersisted(offset)
        }.pipeTo(self)

      case OnNext(any) =>
        log.warning(s"Received something that is not a DomainEvent! $any - [${self.path.name}]")
    }

    receive orElse errorHandling("running projection")
  }

  def acceptingEvents: Receive = {

    val receive: Receive = {
      case OnNextDomainEvent(evt, offset) =>
        log.debug(s"Received event $evt")
        projection.onEvent(evt).map(_ => ProjectionActor.Done(evt)).pipeTo(self)
        context become runningProjection(evt, offset)

      case OnNext(any) => log.warning(s"Receive something that is not a DomainEvent! $any")
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
      log.error(e, s"OnError while $phase ... [${self.path.name}]")
      context.stop(self)

    case Status.Failure(e) => // receive a general error, probably from the projection
      log.error(e, s"Failure while $phase ... [${self.path.name}]")
      context.stop(self)
  }

  def waitingOffsetPersistence(currentEvent: DomainEvent): Receive = {

    case ProjectionActor.OffsetPersisted(offset) =>
      unstashAll()
      lastProcessedOffset = Option(offset)
      context become acceptingEvents

    case Status.Failure(e) =>
      // continue processing anyway,
      // we can only hope that next time we'll be able to save the offset
      context become acceptingEvents

    case anyOther => stash()
  }

  object OnNextDomainEvent {

    def unapply(onNext: OnNext): Option[(DomainEvent, Long)] = {
      onNext.element match {
        case EventEnvelope(offset, _, _, event: DomainEvent) => Option((event, offset))
        case _ => None
      }
    }
  }

}

object ProjectionActor {

  case class Done(evt: DomainEvent)
  case class OffsetPersisted(offset: Long)

}

class ForwardingActorSubscriber(target: ActorRef, val requestStrategy: RequestStrategy) extends ActorSubscriber {

  def receive: Actor.Receive = {

    case onNext: OnNext =>
      target forward onNext

    case onError: OnError =>
      target forward onError
      context.system.stop(self)

  }
}

/**
 * A ProjectionActor that never saves the offset
 * causing the event stream to be read from start on each app restart
 */
class ProjectionActorWithoutOffsetPersistence(
  projection: Projection,
  sourceProvider: EventsSourceProvider
)
    extends ProjectionActor(projection, sourceProvider) with OffsetNotPersisted

object ProjectionActorWithoutOffsetPersistence {

  def props(
    projection: Projection,
    sourceProvider: EventsSourceProvider
  ) = {

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
)
    extends ProjectionActor(projection, sourceProvider) with PersistedOffsetAkka

object ProjectionActorWithOffsetManagedByAkkaPersistence {

  def props(
    projection: Projection,
    sourceProvider: EventsSourceProvider,
    persistenceId: String
  ) = {

    Props(new ProjectionActorWithOffsetManagedByAkkaPersistence(projection, sourceProvider, persistenceId))
  }
}

/** A ProjectionActor that saves the offset using a [[CustomOffsetPersistenceStrategy]] */
class ProjectionActorWithCustomOffsetPersistence(
    projection: Projection,
    sourceProvider: EventsSourceProvider,
    customOffsetPersistence: CustomOffsetPersistenceStrategy
) extends ProjectionActor(projection, sourceProvider) with PersistedOffsetCustom {

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
  ) = {

    Props(new ProjectionActorWithCustomOffsetPersistence(projection, sourceProvider, customOffsetPersistence))
  }

}