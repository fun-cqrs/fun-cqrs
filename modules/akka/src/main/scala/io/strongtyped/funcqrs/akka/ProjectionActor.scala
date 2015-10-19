package io.strongtyped.funcqrs.akka

import akka.actor._
import akka.pattern._
import akka.persistence.{RecoveryCompleted, SnapshotOffer, PersistentActor}
import akka.persistence.query.EventEnvelope
import akka.stream.ActorMaterializer
import akka.stream.actor.ActorSubscriberMessage.{OnError, OnNext}
import akka.stream.actor.{ActorSubscriber, RequestStrategy, WatermarkRequestStrategy}
import akka.stream.scaladsl.Sink
import akka.util.Timeout
import io.strongtyped.funcqrs.{DomainEvent, Projection}

import scala.concurrent.duration._
import scala.language.postfixOps

abstract class ProjectionActor(name: String, projection: Projection) extends PersistentActor with ActorLogging with Stash {
  this: EventsSourceProvider =>


  import context.dispatcher

  implicit val timeout = Timeout(5 seconds)

  var currentOffset: Long = 0

  def saveCurrentOffset(offset: Long): Unit = {
    saveSnapshot(offset)
  }

  override def receiveCommand: Receive = acceptingEvents

  def persistenceId: String = name

  override val receiveRecover: Receive = {

    case SnapshotOffer(metadata, offset: Long) =>
      currentOffset = offset

    case _: RecoveryCompleted =>
      log.debug(s"Recovery completed for ProjectionActor $name")
      recoveryCompleted()

    case unknown => log.debug(s"Unknown message on recovery: $unknown")
  }

  def recoveryCompleted(): Unit = {
    log.debug(s"ProjectionActor: starting projection... $projection")
    implicit val mat = ActorMaterializer()
    val actorSink = Sink.actorSubscriber(Props(classOf[ForwardingActorSubscriber], self, WatermarkRequestStrategy(10)))
    source(currentOffset).runWith(actorSink)
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
      context become acceptingEvents // continue processing if possible
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


  final def handleFailure(evt: DomainEvent, e: Throwable): Unit = {
    val finalHandleFailure = onFailure orElse handleFailureFunc
    finalHandleFailure((evt, e))
  }

  val handleFailureFunc: PartialFunction[(DomainEvent, Throwable), Unit] = {
    // by default we re-throw to kill the actor and reprocess event
    case (currentEvent, e) =>
      log.error(e, s"Failed to process event $currentEvent")
      throw e
  }

  object OnNextDomainEvent {
    def unapply(onNext: OnNext): Option[(DomainEvent, Long)] = {
      onNext.element match {
        case EventEnvelope(offset, _, _, event: DomainEvent) => Option((event, offset))
        case _                                               => None
      }
    }
  }

  def onFailure: PartialFunction[(DomainEvent, Throwable), Unit] = PartialFunction.empty
}

object ProjectionActor {

  case class Done(evt: DomainEvent)

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