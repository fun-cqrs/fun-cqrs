package io.funcqrs.behavior

import java.time.OffsetDateTime

import io.funcqrs.EventId
import io.funcqrs.interpreters.Identity
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
        MaybeOneEvent {
          case CreateTracker => Some(TimerCreated(EventId()))
        }
      }

      // handle command single List[Event]
      .commandHandler {
        MaybeManyEvents {
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
        MaybeManyEvents {
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
        AttemptOneEvent {
          case CreateTracker => Try(TimerCreated(EventId()))
        }
      }

      // handle command single Try[List[Event]]
      .commandHandler {
        AttemptManyEvents {
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
        AttemptManyEvents {
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
        EventuallyOneEvent {
          case CreateTracker => Future.successful(TimerCreated(EventId()))
        }
      }
      // handle command single Future[List[Event]]
      .commandHandler {
        EventuallyManyEvents {
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
        EventuallyManyEvents {
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
