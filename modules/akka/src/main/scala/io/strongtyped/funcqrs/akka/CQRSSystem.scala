package io.strongtyped.funcqrs.akka

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.pattern._
import akka.util.Timeout
import scala.concurrent.Future
import scala.concurrent.duration._

class CQRSSystem(actorSystem: ActorSystem) {

  implicit val actorCreationTimeout = Timeout(200.millis)

  val projectionMonitorActorRef = actorSystem.actorOf(Props(classOf[ProjectionMonitorActor]), "projectionMonitor")

  def projection(props: Props, name: String): Future[ActorRef] = {
    (projectionMonitorActorRef ? ProjectionMonitorActor.CreateProjection(props, name)).mapTo[ActorRef]
  }

  val cqrsContext = new CQRSContext(projectionMonitorActorRef)
}
