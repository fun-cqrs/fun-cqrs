package io.funcqrs

import java.time.OffsetDateTime

import io.funcqrs.behavior.{ Behavior, Uninitialized, _ }
import io.funcqrs.interpreters.IdentityInterpreter
import io.funcqrs.model.TimeTracker.Idle
import io.funcqrs.model.TimerTrackerProtocol.{ TimerCreated, TimerStarted, _ }
import io.funcqrs.model.{ TimeTracker, TrackerId }
import org.scalatest.{ FunSuite, Matchers }
/**
 * The intent of this test is not test a specific Interpreter, but to test the
 * [[io.funcqrs.interpreters.Interpreter]] common behavior
 */
class InterpreterTest extends FunSuite with Matchers {

  val initialState = Uninitialized[TimeTracker](TrackerId.generate)

  test("A interpreter will fail a Command if missing event handlers for a given event") {

    // a bogus TimeTracker behavior
    // can't start timer due to empty actions for Idle state
    def behavior: Behavior[TimeTracker] = {
      case Uninitialized(id) => createActions(id)
      case Idle(tracker) => Actions.empty
    }

    val interpreter = IdentityInterpreter(behavior)

    // missing handler for TimerStarted event
    intercept[MissingEventHandlerException] {
      interpreter.onCommand(initialState, CreateAndStartTracking("test"))
    }
  }

  test("A interpreter will fail a Command if missing behavior for a given state") {
    // a bogus TimeTracker behavior
    // can't start timer due to missing case for Idle state
    def behavior: Behavior[TimeTracker] = {
      case Uninitialized(id) => createActions(id)
    }

    val interpreter = IdentityInterpreter(behavior)

    // missing behavior for Idle state
    intercept[MissingBehaviorException] {
      interpreter.onCommand(initialState, CreateAndStartTracking("test"))
    }
  }

  def createActions(trackerId: TrackerId) =
    actions[TimeTracker]
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
        evt: TimerCreated => TimeTracker(trackerId)
      }
}
