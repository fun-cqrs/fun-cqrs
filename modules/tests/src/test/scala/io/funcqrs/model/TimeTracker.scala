package io.funcqrs.model

import java.time.OffsetDateTime
import java.util.UUID

import io.funcqrs._
import io.funcqrs.behavior._
import io.funcqrs.behavior.{ Behavior, Uninitialized }

case class TimeTracker(id: TrackerId, currentTask: Option[Task] = None, previousTasks: List[Task] = List()) extends AggregateLike {
  type Id = TrackerId
  type Protocol = TimerTrackerProtocol.type

  def isBusy = currentTask.isDefined
  def isIdle = currentTask.isEmpty

  def start(title: String, start: OffsetDateTime) = {
    val task = Task(title, start)
    copy(currentTask = Some(task))
  }

  def stop(end: OffsetDateTime) = {
    currentTask.map { task =>
      val stoppedTask = task.copy(stopped = Some(end))
      copy(currentTask = None, previousTasks = previousTasks :+ task)
    }.getOrElse(this)
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

  def behavior(id: TrackerId): Behavior[TimeTracker] = {

    case Uninitialized(_) =>
      actions[TimeTracker]
        .handleCommand {
          cmd: CreateTracker.type => TimerCreated(EventId(), cmd.id)
        }
        .handleCommand.manyEvents {
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
          evt: TimerCreated => TimeTracker(id)
        }

    case Idle(tracker) =>
      actions[TimeTracker]
        .handleCommand {
          cmd: StartTracking => TimerStarted(cmd.title, OffsetDateTime.now(), EventId(), cmd.id)
        }
        .handleCommand {
          cmd: ReplaceTask => TimerStarted(cmd.title, OffsetDateTime.now(), EventId(), cmd.id)
        }
        .handleEvent {
          evt: TimerStarted => tracker.start(evt.title, evt.start)
        }

    case Busy(tracker) =>
      actions[TimeTracker]
        .handleCommand {
          cmd: StopTracking.type => TimerStopped(OffsetDateTime.now(), EventId(), cmd.id)
        }
        .handleCommand.manyEvents {
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
          evt: TimerStopped => tracker.stop(evt.end)
        }
  }

  class Busy(val state: Initialized[TimeTracker]) extends AnyVal {
    def isEmpty = state.aggregate.isIdle
    def get = state.aggregate
  }

  object Busy {
    def unapply(state: Initialized[TimeTracker]): Busy = new Busy(state)
  }

  class Idle(val obj: Initialized[TimeTracker]) extends AnyVal {
    def isEmpty = obj.aggregate.isBusy
    def get = obj.aggregate
  }

  object Idle {
    def unapply(obj: Initialized[TimeTracker]): Idle = new Idle(obj)
  }

}

case class TrackerId(value: String) extends AggregateId
object TrackerId {
  def generate: TrackerId = TrackerId(UUID.randomUUID().toString)
}

