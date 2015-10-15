package io.strongtyped.funcqrs.akka

import akka.actor._
import akka.pattern._
import akka.stream.ActorMaterializer
import akka.stream.actor.ActorSubscriberMessage.{OnError, OnNext}
import akka.stream.actor.{ActorSubscriber, RequestStrategy, WatermarkRequestStrategy}
import akka.stream.scaladsl.Sink
import akka.util.Timeout
import io.strongtyped.funcqrs.{DomainEvent, Projection}

import scala.concurrent.duration._
import scala.language.postfixOps

abstract class ProjectionActor extends Actor with ActorLogging with Stash {
  this: EventsSourceProvider =>

  def projection: Projection

  import context.dispatcher

  implicit val timeout = Timeout(5 seconds)

  def offset: Long = 0

  override def preStart(): Unit = {
    log.debug(s"ProjectionActor: starting projection... $projection")
    implicit val mat = ActorMaterializer()
    val actorSink = Sink.actorSubscriber(Props(classOf[ForwardingActorSubscriber], self, WatermarkRequestStrategy(10)))
    source(offset).runWith(actorSink)
  }

  def receive: Receive = acceptingEvents

  def runningProjection: Receive = {

    // stash new events while busy with projection
    case OnNext(evt: DomainEvent) => stash()

    // ready with projection, notify parent and start consuming next events
    case ProjectionActor.Done(lastEvent) =>
      log.debug(s"Processed $lastEvent, sending to parent ${context.parent}")
      context.parent ! lastEvent // send last processed event to parent
      unstashAll()
      context become acceptingEvents
  }

  def acceptingEvents: Receive = {

    case OnNext(evt: DomainEvent) =>
      log.debug(s"Received event $evt")
      projection.onEvent(evt).map(_ => ProjectionActor.Done(evt)).pipeTo(self)
      context become runningProjection
  }

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