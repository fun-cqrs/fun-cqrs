package io.funcqrs.behavior

import io.funcqrs.behavior.handlers._
import java.time.OffsetDateTime

import io.funcqrs.model.{CreateTracker, _}
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers

import scala.collection.immutable
import scala.concurrent.Future
import scala.util.Try

/**
  * Test that `Actions` can receive command handlers for all the expected types:
  * - Event (Identity[Event])
  * - immutable.Seq[Event] (Identity[immutable.Seq[Event]])
  * - List[Event] (Identity[List[Event]])
  *
  * - Option[Event]
  * - Option[immutable.Seq[Event]]
  * - Option[List[Event]]
  *
  * - Future[Event]
  * - Future[immutable.Seq[Event]]
  * - Future[List[Event]]
  *
  * - Try[Event]
  * - Try[immutable.Seq[Event]]
  * - Try[List[Event]]
  */
class ActionsTest extends AnyFunSuiteLike with Matchers {

  test("Actions should accept (compile) all default types supported by Fun.CQRS") {
    TimeTracker.actions

    // ---------------------------------------------------------------
    // IDENTITY
    // handle Command to One Event (Identity)
      .commandHandler {
        just.OneEvent {
          case CreateTracker => TimerCreated(OffsetDateTime.now)
        }
      }

      // handle command single List[Event]
      .commandHandler {
        ManyEvents {
          case CreateAndStartTracking(taskTitle) =>
            List(
              TimerCreated(OffsetDateTime.now),
              TimerStarted(taskTitle, OffsetDateTime.now())
            )
        }
      }
      // handle command single immutable.Seq[Event]
      .commandHandler {
        ManyEvents {
          case CreateAndStartTracking(taskTitle) =>
            immutable.Seq(
              TimerCreated(OffsetDateTime.now),
              TimerStarted(taskTitle, OffsetDateTime.now())
            )
        }
      }
      // ---------------------------------------------------------------
      // MAYBE - Option
      // handle Command to One Event (Option)
      .commandHandler {
        maybe.OneEvent {
          case CreateTracker => Some(TimerCreated(OffsetDateTime.now))
        }
      }

      // handle command single List[Event]
      .commandHandler {
        maybe.ManyEvents {
          case CreateAndStartTracking(taskTitle) =>
            Option(
              List(
                TimerCreated(OffsetDateTime.now),
                TimerStarted(taskTitle, OffsetDateTime.now())
              )
            )
        }
      }
      // handle command single immutable.Seq[Event]
      .commandHandler {
        maybe.ManyEvents {
          case CreateAndStartTracking(taskTitle) =>
            Option(
              immutable.Seq(
                TimerCreated(OffsetDateTime.now),
                TimerStarted(taskTitle, OffsetDateTime.now())
              )
            )
        }
      }
      // ---------------------------------------------------------------
      // ATTEMPT - Try
      // handle command single Try[Event]
      .commandHandler {
        attempt.OneEvent {
          case CreateTracker => Try(TimerCreated(OffsetDateTime.now))
        }
      }

      // handle command single Try[List[Event]]
      .commandHandler {
        attempt.ManyEvents {
          case CreateAndStartTracking(taskTitle) =>
            Try {
              List(
                TimerCreated(OffsetDateTime.now),
                TimerStarted(taskTitle, OffsetDateTime.now())
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
                TimerCreated(OffsetDateTime.now),
                TimerStarted(taskTitle, OffsetDateTime.now())
              )
            }
        }
      }
      // ---------------------------------------------------------------
      // EVENTUALLY - Future
      // handle command single Future[Event]
      .commandHandler {
        eventually.OneEvent {
          case CreateTracker => Future.successful(TimerCreated(OffsetDateTime.now))
        }
      }
      // handle command single Future[List[Event]]
      .commandHandler {
        eventually.ManyEvents {
          case CreateAndStartTracking(taskTitle) =>
            Future.successful {
              List(
                TimerCreated(OffsetDateTime.now),
                TimerStarted(taskTitle, OffsetDateTime.now())
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
                TimerCreated(OffsetDateTime.now),
                TimerStarted(taskTitle, OffsetDateTime.now())
              )
            }
        }
      }
  }
}
