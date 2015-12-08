package io.funcqrs.akka

import _root_.akka.actor.ActorRef
import _root_.akka.pattern._
import _root_.akka.util.Timeout
import io.funcqrs._
import io.funcqrs.akka.AggregateActor._
import io.funcqrs.akka.EventsMonitorActor.Subscribe

import scala.collection.immutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal

class ProjectionMonitor[A <: AggregateLike](projectionName: String, newEventsMonitor: (CommandId) => Future[ActorRef])
    extends AggregateAliases {

  type Aggregate = A

  trait ProjectionCreateResult[Event] {
    def event: Event
  }
  case class ProjectionCreateSuccess[Event](event: Event) extends ProjectionCreateResult[Event]
  case class ProjectionCreateFailure[Event](event: Event, throwable: Throwable) extends ProjectionCreateResult[Event]

  trait ProjectionUpdateResult[Event] {
    def events: immutable.Seq[Event]
  }
  case class ProjectionUpdateSuccess[Event](events: immutable.Seq[Event]) extends ProjectionUpdateResult[Event]
  case class ProjectionUpdateFailure[Event](events: immutable.Seq[Event], throwable: Throwable) extends ProjectionUpdateResult[Event]

  /** Watch for an [[DomainEvent]] originated from the passed [[DomainCommand]] until it's applied to the ReadModel.
    *
    * @param cmd - a [[DomainCommand]] to be sent
    * @param block - a function that will send the `cmd` to the Write Model.
    * @param timeout - an implicit (or explicit) [[Timeout]] after which this call will return a failed Future
    *
    * @return - A Future with a [[ProjectionUpdateResult]]. A [[ProjectionUpdateSuccess]] is returned iff the events originated from `cmd`
    *     are effectively applied on the Read Model, otherwise a [[ProjectionUpdateFailure]] is returned containing the Events and the Exception
    *     indicating the cause of the failure on the Read Model.
    *     Returns a failed Future if `Command` is not valid in which case no Events are generated.
    */
  def watchEvent(cmd: Command)(block: Command => Future[Any])(implicit timeout: Timeout): Future[ProjectionCreateResult[Event]] = {

    val resultOnWrite =
      for {
        // initialize an EventMonitor for the given command
        monitor <- newEventsMonitor(cmd.id)
        // send command to Write Model (AggregateManager)
        event <- block(cmd).mapTo[Event]
      } yield (monitor, event)

    val resultOnRead =
      for {
        (monitor, event) <- resultOnWrite
        // subscribe for the event on the Read Model
        result <- (monitor ? Subscribe(event)).mapTo[EventsMonitorActor.Done.type]
      } yield ProjectionCreateSuccess(event)

    resultOnRead.recoverWith {
      // on failure, we send the event we got from the Write Model
      // together with the exception that made it fail (probably a timeout)
      case NonFatal(e) => resultOnWrite.map { case (_, evt) => ProjectionCreateFailure(evt, e) }
    }
  }

  /** Watch for [[DomainEvent]]s originated from the passed [[DomainCommand]] until they are applied to the ReadModel.
    *
    * @param cmd - a [[DomainCommand]] to be sent
    * @param block - a function that will send the `cmd` to the Write Model.
    * @param timeout - an implicit (or explicit) [[Timeout]] after which this call will return a failed Future
    *
    * @return - A Future with a [[ProjectionUpdateResult]]. A [[ProjectionUpdateSuccess]] is returned iff the events originated from `cmd`
    *     are effectively applied on the Read Model, otherwise a [[ProjectionUpdateFailure]] is returned containing the Events and the Exception
    *     indicating the cause of the failure on the Read Model.
    *     Returns a failed Future if `Command` is not valid in which case no Events are generated.
    */
  def watchEvents(cmd: Command)(block: Command => Future[Any])(implicit timeout: Timeout): Future[ProjectionUpdateResult[Event]] = {

    val resultOnWrite =
      for {
        // initialize an EventMonitor for the given command
        monitor <- newEventsMonitor(cmd.id)

        // send command to Write Model (AggregateManager)
        events <- block(cmd).mapTo[Events]
      } yield (monitor, events)

    val resultOnRead =
      for {
        (monitor, events) <- resultOnWrite
        // subscribe for events on the Read Model
        result <- (monitor ? Subscribe(events)).mapTo[EventsMonitorActor.Done.type]
      } yield ProjectionUpdateSuccess(events)

    resultOnRead.recoverWith {
      // on failure, we send the events we got from the Write Model
      // together with the exception that made it fail (probably a timeout)
      case NonFatal(e) => resultOnWrite.map { case (_, evts) => ProjectionUpdateFailure(evts, e) }
    }
  }
}

