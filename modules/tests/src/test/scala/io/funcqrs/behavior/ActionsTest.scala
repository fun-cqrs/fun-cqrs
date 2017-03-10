package io.funcqrs.behavior

import io.funcqrs.behavior.handlers._

import java.time.OffsetDateTime

import io.funcqrs.EventId
import io.funcqrs.model.{ CreateTracker, _ }
import org.scalatest.{ FunSuite, Matchers }

import scala.collection.immutable
import scala.concurrent.Future
import scala.util.Try

/**
  * Test that `Actions` can receive command handlers for all the expected types:
  * - Event (Identity[Event])
  * - immutable.Seq[Event] (Identity[immutable.Seq[Event]])
  * - List[Event] (Identity[List[Event]])
  *
  * - Future[Event]
  * - Future[immutable.Seq[Event]]
  * - Future[List[Event]]
  *
  * - Try[Event]
  * - Try[immutable.Seq[Event]]
  * - Try[List[Event]]
  */
class ActionsTest extends FunSuite with Matchers {

  test("Actions should accept (compile) all default types supported by Fun.CQRS") {
    TimeTracker.actions

    // ---------------------------------------------------------------
    // IDENTITY
    // handle Command to One Event (Identity)
      .commandHandler {
        OneEvent {
          case CreateTracker => TimerCreated(EventId())
        }
      }

      // handle command single List[Event]
      .commandHandler {
        ManyEvents {
          case CreateAndStartTracking(taskTitle) =>
            List(
              TimerCreated(EventId()),
              TimerStarted(taskTitle, OffsetDateTime.now(), EventId())
            )
        }
      }
      // handle command single immutable.Seq[Event]
      .commandHandler {
        ManyEvents {
          case CreateAndStartTracking(taskTitle) =>
            immutable.Seq(
              TimerCreated(EventId()),
              TimerStarted(taskTitle, OffsetDateTime.now(), EventId())
            )
        }
      }
      // ---------------------------------------------------------------
      // MAYBE - Option
      // handle Command to One Event (Option)
      .commandHandler {
        option.OneEvent {
          case CreateTracker => Some(TimerCreated(EventId()))
        }
      }

      // handle command single List[Event]
      .commandHandler {
        option.ManyEvents {
          case CreateAndStartTracking(taskTitle) =>
            Option(
              List(
                TimerCreated(EventId()),
                TimerStarted(taskTitle, OffsetDateTime.now(), EventId())
              )
            )
        }
      }
      // handle command single immutable.Seq[Event]
      .commandHandler {
        option.ManyEvents {
          case CreateAndStartTracking(taskTitle) =>
            Option(
              immutable.Seq(
                TimerCreated(EventId()),
                TimerStarted(taskTitle, OffsetDateTime.now(), EventId())
              )
            )
        }
      }
      // ---------------------------------------------------------------
      // ATTEMPT - Try
      // handle command single Try[Event]
      .commandHandler {
        attempt.OneEvent {
          case CreateTracker => Try(TimerCreated(EventId()))
        }
      }

      // handle command single Try[List[Event]]
      .commandHandler {
        attempt.ManyEvents {
          case CreateAndStartTracking(taskTitle) =>
            Try {
              List(
                TimerCreated(EventId()),
                TimerStarted(taskTitle, OffsetDateTime.now(), EventId())
              )
            }
        }
      }

      // handle command single Try[immutable.Seq[Event]]
      .commandHandler {
        attempt.ManyEvents {
          case CreateAndStartTracking(taskTitle) =>
            Try {
              immutable.Seq(
                TimerCreated(EventId()),
                TimerStarted(taskTitle, OffsetDateTime.now(), EventId())
              )
            }
        }
      }
      // ---------------------------------------------------------------
      // EVENTUALLY - Future
      // handle command single Future[Event]
      .commandHandler {
        eventually.OneEvent {
          case CreateTracker => Future.successful(TimerCreated(EventId()))
        }
      }
      // handle command single Future[List[Event]]
      .commandHandler {
        eventually.ManyEvents {
          case CreateAndStartTracking(taskTitle) =>
            Future.successful {
              List(
                TimerCreated(EventId()),
                TimerStarted(taskTitle, OffsetDateTime.now(), EventId())
              )
            }
        }
      }
      // handle command single Future[immutable.Seq[Event]]
      .commandHandler {
        eventually.ManyEvents {
          case CreateAndStartTracking(taskTitle) =>
            Future.successful {
              immutable.Seq(
                TimerCreated(EventId()),
                TimerStarted(taskTitle, OffsetDateTime.now(), EventId())
              )
            }
        }
      }
  }
}
