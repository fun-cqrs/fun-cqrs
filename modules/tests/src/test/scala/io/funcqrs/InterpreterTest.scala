package io.funcqrs

import java.time.OffsetDateTime

import io.funcqrs.behavior._
import io.funcqrs.interpreters.IdentityInterpreter
import io.funcqrs.model._
import org.scalatest.{ FunSuite, Matchers }

/**
  * The intent of this test is not test a specific Interpreter, but to test the
  * [[io.funcqrs.interpreters.Interpreter]] common behavior
  */
class InterpreterTest extends FunSuite with Matchers {

  val initialState: Option[TimeTracker] = None

  test("A interpreter will fail a Command if missing behavior for a given state") {
    // a bogus TimeTracker behavior
    // can't start timer due to missing case for Idle state
    def behavior =
      Behavior
        .construct(constructionHandlers(TrackerId.generate))
        .andThen {
          // missing behavior for IdleTracker
          case _: BusyTracker => Actions.empty
        }

    val interpreter = IdentityInterpreter(behavior)

    // missing behavior for Idle state
    intercept[MissingBehaviorException] {
      interpreter.applyCommand(initialState, CreateAndStartTracking("test"))
    }
  }

  def constructionHandlers(trackerId: TrackerId) =
    TimeTracker.actions
      .handleCommand { cmd: CreateTracker.type =>
        TimerCreated(EventId())
      }
      .handleCommand { cmd: CreateAndStartTracking =>
        List(
          TimerCreated(EventId()),
          // the event handler for TimerStarter depends on a created Tracker
          // and therefore can't be defined in this 'Actions'
          // behavior is available in next transition
          TimerStarted(cmd.taskTitle, OffsetDateTime.now(), EventId())
        )
      }
      .handleEvent { evt: TimerCreated =>
        IdleTracker(trackerId)
      }
}
