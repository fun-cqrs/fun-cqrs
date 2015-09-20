package fun.cqrs.akka

import akka.actor._
import akka.stream.ActorMaterializer
import akka.stream.actor.ActorSubscriberMessage.{OnError, OnNext}
import akka.stream.actor.{ActorSubscriber, RequestStrategy, WatermarkRequestStrategy}
import akka.stream.scaladsl.{Sink, Source}
import akka.util.Timeout
import fun.cqrs.{DomainEvent, Projection}
import akka.pattern._
import scala.language.postfixOps
import scala.concurrent.duration._

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

  def handlingEvents: Receive = {

    case OnNext(evt: DomainEvent) =>
      log.debug(s"ProjectionActor: Received event $evt")

      projection.onEvent(evt).map(_ => ProjectionActor.Done).pipeTo(self)
      context become processingEvents

  }

  def processingEvents: Receive = {
    case OnNext(evt: DomainEvent) => stash()
    case ProjectionActor.Done     =>
      unstashAll()
      context become handlingEvents
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