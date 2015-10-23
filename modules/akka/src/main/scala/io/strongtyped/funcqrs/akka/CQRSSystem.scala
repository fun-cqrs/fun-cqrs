package io.strongtyped.funcqrs.akka

import akka.actor.{ ActorRef, ActorSystem, Props }
import java.util.UUID
import akka.actor.{ ActorRef, ActorSystem, Props }
import akka.pattern._
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import io.strongtyped.funcqrs.Projection
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.reflect.ClassTag
import scala.util.{ Failure, Success, Try }

class CQRSSystem(actorSystem: ActorSystem) extends LazyLogging {

  implicit val actorCreationTimeout = Timeout(200.millis)

  val projectionMonitorActorRef = actorSystem.actorOf(Props(classOf[ProjectionMonitorActor]), "projectionMonitor")

  /** Adds a new [[ProjectionActor]].
    *
    * Note that a ProjectionActor is required to have a unique name.
    * This unique name is used to identify this Actor but also as persistenceId for the offset.
    *
    * Moreover, a ProjectionActor is required to have a constructor receiving name:String and projection:Projection
    * as the first two parameters.
    */
  def projection[P <: ProjectionActor](name: String, projection: Projection, args: AnyRef*)(implicit classTag: ClassTag[P]): Future[ActorRef] = {

    val propsArgs = Seq(name, projection) ++ args.toSeq

    val props = Props(classTag.runtimeClass, propsArgs: _*)

    (projectionMonitorActorRef ? ProjectionMonitorActor.CreateProjection(props, name)).mapTo[ActorRef]
  }

  val cqrsContext = new CQRSContext(projectionMonitorActorRef)
}
