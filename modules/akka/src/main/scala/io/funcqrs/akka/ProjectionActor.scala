package io.funcqrs.akka

import akka.actor._
import akka.pattern._
import akka.persistence.query.EventEnvelope
import akka.stream.ActorMaterializer
import akka.stream.actor.ActorSubscriberMessage.{ OnError, OnNext }
import akka.stream.actor.{ ActorSubscriber, RequestStrategy, WatermarkRequestStrategy }
import akka.stream.scaladsl.Sink
import akka.util.Timeout
import io.funcqrs.akka.FunCQRS.api.CustomOffsetPersistenceStrategy
import io.funcqrs.akka.ProjectionActor.FailureStrategy
import io.funcqrs.{ DomainEvent, Projection }

import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }
import scala.language.postfixOps

// TODO: document each parameter
abstract class ProjectionActor(projection: Projection,
                               sourceProvider: EventsSourceProvider,
                               failureStrategy: FailureStrategy)
    extends Actor with ActorLogging with Stash {

  import context.dispatcher

  implicit val timeout = Timeout(5 seconds)

  var lastProcessedOffset: Option[Long] = None

  def saveCurrentOffset(offset: Long): Unit

  def recoveryCompleted(): Unit = {
    log.debug(s"ProjectionActor: starting projection... $projection")
    implicit val mat = ActorMaterializer()
    val actorSink = Sink.actorSubscriber(Props(classOf[ForwardingActorSubscriber], self, WatermarkRequestStrategy(10)))
    sourceProvider.source(lastProcessedOffset.map(_ + 1).getOrElse(0)).runWith(actorSink)
  }

  override def receive: Receive = acceptingEvents

  def runningProjection(currentEvent: DomainEvent, offset: Long): Receive = {

    // stash new events while busy with projection
    case OnNextDomainEvent(_, _) => stash()

    // ready with projection, notify parent and start consuming next events
    case ProjectionActor.Done(lastEvent) =>
      log.debug(s"Processed $lastEvent, sending to parent ${context.parent}")
      context.parent ! lastEvent // send last processed event to parent

      // save offset of last processed event
      // event will be processed twice if saveCurrentOffset fails
      // therefore Projections should be idempotent or fail-safe
      saveCurrentOffset(offset)

      unstashAll()
      context become acceptingEvents

    case OnNext(any) => log.warning(s"Receive something that is not a DomainEvent! $any")

    case Status.Failure(e) =>
      handleFailure(currentEvent, e)
      context become handlingFailure // wait till failure is handled
  }

  def acceptingEvents: Receive = {

    case OnNextDomainEvent(evt, offset) =>
      log.debug(s"Received event $evt")
      projection.onEvent(evt).map(_ => ProjectionActor.Done(evt)).pipeTo(self)
      context become runningProjection(evt, offset)

    case OnNext(any) => log.warning(s"Receive something that is not a DomainEvent! $any")

    case Status.Failure(e) =>
      log.error(e, "Failure while accepting events...")
      throw e
  }

  def handlingFailure: Receive = {

    case h: ProjectionActor.DoneHandlingFailure =>
      unstashAll()
      context become acceptingEvents

    case _ => stash()
  }

  final def handleFailure(evt: DomainEvent, e: Throwable): Unit = {
    val finalHandleFailure = failureStrategy orElse handleFailureFunc
    finalHandleFailure((evt, e)).map { _ =>
      ProjectionActor.DoneHandlingFailure(evt, e)
    }.pipeTo(self)
  }

  val handleFailureFunc: PartialFunction[(DomainEvent, Throwable), Future[Unit]] = {
    // by default we re-throw to kill the actor and reprocess event
    case (currentEvent, e) =>
      log.error(e, s"Failed to process event $currentEvent")
      throw e
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

  /**
   * PartialFunction to handle failures while processing events inside a [[ProjectionActor]].
   *
   * Typically, such a strategy should handle exceptions that are NOT related with external system calls or database operations.
   * Only programatically errors should be handled. This is useful to avoid that a ProjectionActor get stuck trying processing an event
   * it can't handle due to a programing error.
   *
   */
  type FailureStrategy = PartialFunction[(DomainEvent, Throwable), Future[Unit]]

  case class Done(evt: DomainEvent)
  case class DoneHandlingFailure(evt: DomainEvent, throwable: Throwable)

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
class ProjectionActorWithoutOffsetPersistence(projection: Projection,
                                              sourceProvider: EventsSourceProvider,
                                              failureStrategy: FailureStrategy)
    extends ProjectionActor(projection, sourceProvider, failureStrategy) with OffsetNotPersisted

object ProjectionActorWithoutOffsetPersistence {

  def props(projection: Projection,
            sourceProvider: EventsSourceProvider,
            failureStrategy: FailureStrategy) = {

    Props(new ProjectionActorWithoutOffsetPersistence(projection, sourceProvider, failureStrategy))
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
class ProjectionActorWithOffsetManagedByAkkaPersistence(projection: Projection,
                                                        sourceProvider: EventsSourceProvider,
                                                        failureStrategy: FailureStrategy,
                                                        val persistenceId: String)
    extends ProjectionActor(projection, sourceProvider, failureStrategy) with PersistedOffsetAkka

object ProjectionActorWithOffsetManagedByAkkaPersistence {

  def props(projection: Projection,
            sourceProvider: EventsSourceProvider,
            failureStrategy: FailureStrategy,
            persistenceId: String) = {

    Props(new ProjectionActorWithOffsetManagedByAkkaPersistence(projection, sourceProvider, failureStrategy, persistenceId))
  }
}

/** A ProjectionActor that saves the offset using a [[CustomOffsetPersistenceStrategy]] */
class ProjectionActorWithCustomOffsetPersistence(projection: Projection,
                                                 sourceProvider: EventsSourceProvider,
                                                 failureStrategy: FailureStrategy,
                                                 customOffsetPersistence: CustomOffsetPersistenceStrategy)

    extends ProjectionActor(projection, sourceProvider, failureStrategy) with PersistedOffsetCustom {

  def saveCurrentOffset(offset: Long): Unit = {
    // TODO: change signature of OffsetPersistence.saveCurrentOffset to return Future[Unit]
    Await.result(customOffsetPersistence.saveCurrentOffset(offset), 500.millis)
  }

  /** Returns the current offset as persisted in DB */
  def readOffset: Future[Option[Long]] = customOffsetPersistence.readOffset
}

object ProjectionActorWithCustomOffsetPersistence {

  def props(projection: Projection,
            sourceProvider: EventsSourceProvider,
            failureStrategy: FailureStrategy,
            customOffsetPersistence: CustomOffsetPersistenceStrategy) = {

    Props(new ProjectionActorWithCustomOffsetPersistence(projection, sourceProvider, failureStrategy, customOffsetPersistence))
  }

}