package io.strongtyped.funcqrs.akka

import java.util.UUID

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.pattern._
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import io.strongtyped.funcqrs.Projection
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

class CQRSSystem(actorSystem: ActorSystem) extends LazyLogging {

  implicit val actorCreationTimeout = Timeout(200.millis)

  val projectionMonitorActorRef = actorSystem.actorOf(Props(classOf[ProjectionMonitorActor]), "projectionMonitor")

  /**
   * Adds a new [[ProjectionActor]].
   *
   * Note that a ProjectionActor is required to have a unique name.
   * This unique name is used to identify this Actor but also as persistenceId for the offset.
   *
   * Moreover, a ProjectionActor is required to have a constructor receiving the name:String and the projection:Projection.
   */
  def projection[P <: ProjectionActor](name: String, projection: Projection)(implicit classTag: ClassTag[P]): Future[ActorRef] = {
    val props = Props(classTag.runtimeClass, name, projection)
    (projectionMonitorActorRef ? ProjectionMonitorActor.CreateProjection(props, name)).mapTo[ActorRef]
  }

  val cqrsContext = new CQRSContext(projectionMonitorActorRef)
}
