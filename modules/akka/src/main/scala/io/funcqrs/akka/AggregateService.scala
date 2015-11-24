package io.funcqrs.akka

import akka.actor.ActorRef
import akka.pattern._
import akka.util.Timeout
import io.funcqrs.{AggregateAliases, AggregateLike, CommandId}

import scala.concurrent.Future

trait AggregateService[A <: AggregateLike] extends AggregateAliases {
  service =>

  type Aggregate = A

  def aggregateManager: ActorRef

  def projectionMonitorActorRef: ActorRef

  def sendCommand(id: Id)(cmd: Command): AggregateUpdateInvokerWriteModel = {
    AggregateUpdateInvokerWriteModel(id, cmd)
  }

  case class AggregateUpdateInvokerWriteModel(id: Id, cmd: Command) {

    def watch(projectionName: String): AggregateUpdateInvokerReadModel =
      AggregateUpdateInvokerReadModel(projectionName, id, cmd)

    def result()(implicit timeout: Timeout): Future[Events] = {
      (aggregateManager ? (id, cmd)).mapTo[Events]
    }
  }

  case class AggregateUpdateInvokerReadModel(projectionName: String, id: Id, cmd: Command) {

    def result()(implicit timeout: Timeout): Future[ProjectionMonitor[A]#ProjectionResult[ProjectionMonitor[A]#Event]] = {
      projectionMonitor(projectionName).watchEvents(cmd) { _ =>
        (aggregateManager ? (id, cmd)).mapTo[Events]
      }
    }
  }

  /** Builds a ProjectionMonitor actor that can inform when events from a given command have been applied
    * to the read model.
    */
  protected def projectionMonitor(viewName: String)(implicit timeout: Timeout): ProjectionMonitor[A] = {

    val newEventsMonitor = (commandId: CommandId) => {
      (projectionMonitorActorRef ? ProjectionMonitorActor.EventsMonitorRequest(commandId, viewName)).mapTo[ActorRef]
    }

    new ProjectionMonitor[A](viewName, newEventsMonitor)
  }

}

trait AggregateServiceWithAssignedId[A <: AggregateLike] extends AggregateService[A] {
  service =>

  def newInstance(id: Id, cmd: Command): AggregateConsInvokerWriteModel = {
    AggregateConsInvokerWriteModel(id, cmd)
  }

  case class AggregateConsInvokerWriteModel(id: Id, cmd: Command) {

    def watch(projectionName: String): AggregateUpdateConsReadModel =
      AggregateUpdateConsReadModel(projectionName, id, cmd)

    def result()(implicit timeout: Timeout): Future[Event] = {
      (aggregateManager ? (id, cmd)).mapTo[Event]
    }
  }

  case class AggregateUpdateConsReadModel(projectionName: String, id: Id, cmd: Command) {

    def result()(implicit timeout: Timeout): Future[ProjectionMonitor[A]#ProjectionResult[ProjectionMonitor[A]#Event]] = {
      projectionMonitor(projectionName).watchEvent(cmd) { _ =>
        (aggregateManager ? (id, cmd)).mapTo[Event]
      }
    }
  }
}

trait AggregateServiceWithManagedId[A <: AggregateLike] extends AggregateService[A] {
  service =>

  def newInstance(cmd: Command): AggregateConsInvokerWriteModel = {
    AggregateConsInvokerWriteModel(cmd)
  }

  case class AggregateConsInvokerWriteModel(cmd: Command) {

    def watch(projectionName: String): AggregateUpdateConsReadModel =
      AggregateUpdateConsReadModel(projectionName, cmd)

    def result()(implicit timeout: Timeout): Future[Event] = {
      (aggregateManager ? cmd).mapTo[Event]
    }
  }

  case class AggregateUpdateConsReadModel(projectionName: String, cmd: Command) {

    def result()(implicit timeout: Timeout): Future[ProjectionMonitor[A]#ProjectionResult[ProjectionMonitor[A]#Event]] = {
      projectionMonitor(projectionName).watchEvent(cmd) { _ =>
        (aggregateManager ? cmd).mapTo[Event]
      }
    }
  }
}