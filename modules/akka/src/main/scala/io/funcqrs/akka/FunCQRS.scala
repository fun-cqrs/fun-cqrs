package io.funcqrs.akka

import _root_.akka.actor.{ ActorRef, ActorSystem, Props }
import _root_.akka.pattern._
import _root_.akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import io.funcqrs._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.implicitConversions
import scala.reflect.ClassTag

class FunCQRS(val actorSystem: ActorSystem) extends LazyLogging {

  // Timeout for the actor creation response. Certainly exaggerated!!
  implicit val actorCreationTimeout = Timeout(3.seconds)

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

  def projectionMonitor[A <: AggregateLike](viewName: String): ProjectionMonitor[A] = {

    /** Builds a EventsMonitor actor that can inform when events from a given command have been applied
      * to the read model.
      */
    val newEventsMonitor = (commandId: CommandId) => {
      (projectionMonitorActorRef ? ProjectionMonitorActor.EventsMonitorRequest(commandId, viewName)).mapTo[ActorRef]
    }

    new ProjectionMonitor[A](viewName, newEventsMonitor)
  }

}
