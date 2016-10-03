package io.funcqrs.model

import java.time.OffsetDateTime
import java.util.UUID

import io.funcqrs._
import io.funcqrs.behavior._

sealed trait TimeTracker extends AggregateLike {

  type Id = TrackerId
  type Protocol = TimerTrackerProtocol.type

  def id: TrackerId
  def previousTasks: List[Task]

  def isBusy =
    this match {
      case _: BusyTracker => true
      case _ => false
    }

  def isIdle = !isBusy
}

case class BusyTracker(id: TrackerId, currentTask: Task, previousTasks: List[Task] = List()) extends TimeTracker {

  import TimerTrackerProtocol._

  def stop(end: OffsetDateTime): TimeTracker = {
    IdleTracker(id, previousTasks :+ currentTask)
  }

  def actionsWhenBusy =
    actions[TimeTracker]
      .handleCommand {
        cmd: StopTracking.type => TimerStopped(OffsetDateTime.now(), EventId(), cmd.id)
      }
      .handleCommand {
        cmd: ReplaceTask =>
          val now = OffsetDateTime.now()
          List(
            TimerStopped(now, EventId(), cmd.id),
            // the event handler for TimerStarter is defined
            // on the behavior case branch for idling TimeTracker
            TimerStarted(cmd.title, now, EventId(), cmd.id)
          )
      }
      .handleEvent {
        evt: TimerStopped => stop(evt.end)
      }
}

case class IdleTracker(id: TrackerId, previousTasks: List[Task] = List()) extends TimeTracker {

  import TimerTrackerProtocol._

  def start(title: String, start: OffsetDateTime): TimeTracker = {
    val task = Task(title, start)
    BusyTracker(id, task, previousTasks)
  }

  def actionsWhenIdle =
    actions[TimeTracker]
      .handleCommand {
        cmd: StartTracking => TimerStarted(cmd.title, OffsetDateTime.now(), EventId(), cmd.id)
      }
      .handleCommand {
        cmd: ReplaceTask => TimerStarted(cmd.title, OffsetDateTime.now(), EventId(), cmd.id)
      }
      .handleEvent {
        evt: TimerStarted => start(evt.title, evt.start)
      }

}

case class Task(title: String, started: OffsetDateTime, stopped: Option[OffsetDateTime] = None) {
  def isRunning = stopped.isEmpty
}

object TimerTrackerProtocol extends ProtocolLike {

  case object CreateTracker extends ProtocolCommand
  case class CreateAndStartTracking(taskTitle: String) extends ProtocolCommand

  case class StartTracking(title: String) extends ProtocolCommand
  case class ReplaceTask(title: String) extends ProtocolCommand
  case object StopTracking extends ProtocolCommand

  case class TimerCreated(id: EventId, commandId: CommandId) extends ProtocolEvent
  case class TimerStarted(title: String, start: OffsetDateTime, id: EventId, commandId: CommandId) extends ProtocolEvent
  case class TimerStopped(end: OffsetDateTime, id: EventId, commandId: CommandId) extends ProtocolEvent
}
object TimeTracker {

  import TimerTrackerProtocol._

  def factoryActions(id: TrackerId) =
    actions[TimeTracker]
      .handleCommand {
        cmd: CreateTracker.type => TimerCreated(EventId(), cmd.id)
      }
      .handleCommand {
        cmd: CreateAndStartTracking =>
          List(
            TimerCreated(EventId(), cmd.id),
            // the event handler for TimerStarter depends on a created Tracker
            // and therefore can't be defined in this 'Actions'
            // behavior is available in next transition
            TimerStarted(cmd.taskTitle, OffsetDateTime.now(), EventId(), cmd.id)
          )
      }
      .handleEvent {
        evt: TimerCreated => IdleTracker(id)
      }

  def behavior(id: TrackerId): Behavior[TimeTracker] =
    Behavior {
      factoryActions(id)
    } {
      case tracker: IdleTracker => tracker.actionsWhenIdle
      case tracker: BusyTracker => tracker.actionsWhenBusy
    }

}

case class TrackerId(value: String) extends AggregateId
object TrackerId {
  def generate: TrackerId = TrackerId(UUID.randomUUID().toString)
}

