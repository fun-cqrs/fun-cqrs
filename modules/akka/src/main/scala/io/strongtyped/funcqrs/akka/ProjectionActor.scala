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
  this: ProjectionSource =>

  def projection: Projection

  import context.dispatcher

  implicit val timeout = Timeout(5 seconds)

  override def preStart(): Unit = {
    println(s"ProjectionActor: starting projection... $projection")
    implicit val mat = ActorMaterializer()
    val actorSink = Sink.actorSubscriber(Props(classOf[ForwardingActorSubscriber], self, WatermarkRequestStrategy(10)))
    source.runWith(actorSink)
  }

  def receive: Receive = handlingEvents

  def processingEvents: Receive = {
    case OnNext(evt: DomainEvent) => stash()
    case ProjectionActor.Done     =>
      unstashAll()
      context become handlingEvents
  }

  def handlingEvents: Receive = {
    case OnNext(evt: DomainEvent) =>
      log.debug(s"Received event $evt")

      projection.onEvent(evt).map(_ => ProjectionActor.Done).pipeTo(self)
      context become processingEvents
  }

}

object ProjectionActor {

  case object Done

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