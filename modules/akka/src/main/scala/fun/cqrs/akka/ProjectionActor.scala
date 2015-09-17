package fun.cqrs.akka

import akka.actor._
import akka.stream.ActorMaterializer
import akka.stream.actor.ActorSubscriberMessage.{OnError, OnNext}
import akka.stream.actor.{ActorSubscriber, RequestStrategy, WatermarkRequestStrategy}
import akka.stream.scaladsl.{Sink, Source}
import fun.cqrs.{DomainEvent, Projection}

import scala.language.postfixOps


abstract class ProjectionActor extends Actor with ActorLogging {
  this: ProjectionSource =>

  def projection: Projection

  override def preStart(): Unit = {
    implicit val mat = ActorMaterializer()
    val actorSink = Sink.actorSubscriber(Props(classOf[ForwardingActorSubscriber], self, WatermarkRequestStrategy(10)))
    source.runWith(actorSink)

  }

  def receive: Receive = {
    case OnNext(evt: DomainEvent) =>
      log.debug(s"Received event $evt")
      projection.onEvent(evt)
    case x                        => println(s"Received something else $x")
  }
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