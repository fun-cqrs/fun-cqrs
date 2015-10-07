package io.strongtyped.funcqrs.akka

import akka.actor.ActorRef
import akka.pattern._
import akka.util.Timeout
import io.strongtyped.funcqrs.akka.EventsMonitorActor.Subscribe
import io.strongtyped.funcqrs.{CommandId, DomainCommand, DomainEvent}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class CQRSContext(projectionMonitorActor: ActorRef) {


  /**
   * Builds a EventsMonitor actor that can inform when events from a given command have been applied
   * to the read model.
   *
   * @param commandId - id of DomainCommand that generated the DomainEvents
   */
  private def newEventsMonitor(commandId: CommandId): Future[ActorRef] = {
    // Timeout for the actor creation response. Certainly exaggerated!!
    implicit val timeout = Timeout(3.seconds)
    (projectionMonitorActor ? ProjectionMonitorActor.EventsMonitorRequest(commandId)).mapTo[ActorRef]
  }

  def watchEvent[C <: DomainCommand, E <: DomainEvent](cmd: C)(block: C => Future[E])(implicit timeout: Timeout): Future[E] = {
    val monitorRefFuture = newEventsMonitor(cmd.id)
    for {
      monitor <- monitorRefFuture
      event <- block(cmd)
      result <- (monitor ? Subscribe(Seq(event))).mapTo[EventsMonitorActor.Done.type]
    } yield {
      event
    }
  }

  def watchEvents[C <: DomainCommand, E <: DomainEvent](cmd: C)(block: C => Future[Seq[E]])(implicit timeout: Timeout): Future[Seq[E]] = {
    val monitorRefFuture = newEventsMonitor(cmd.id)
    for {
      monitor <- monitorRefFuture
      events <- block(cmd)
      result <- (monitor ? Subscribe(events)).mapTo[EventsMonitorActor.Done.type]
    } yield {
      events
    }
  }
}
