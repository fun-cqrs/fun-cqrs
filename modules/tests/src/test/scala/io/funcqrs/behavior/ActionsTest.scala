package io.funcqrs.behavior

import java.time.OffsetDateTime

import io.funcqrs.EventId
import io.funcqrs.model.{ IdleTracker, TimeTracker }
import io.funcqrs.model.TimerTrackerProtocol._
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
    actions[TimeTracker]

      // handle command single event
      .handleCommand {
        cmd: CreateTracker.type => TimerCreated(EventId(), cmd.id)
      }

      // handle command single List[Event]
      .handleCommand {
        cmd: CreateAndStartTracking =>
          List(
            TimerCreated(EventId(), cmd.id),
            TimerStarted(cmd.taskTitle, OffsetDateTime.now(), EventId(), cmd.id)
          )
      }

      // handle command single immutable.Seq[Event]
      .handleCommand {
        cmd: CreateAndStartTracking =>
          immutable.Seq(
            TimerCreated(EventId(), cmd.id),
            TimerStarted(cmd.taskTitle, OffsetDateTime.now(), EventId(), cmd.id)
          )
      }

      // handle command single Try[Event]
      .handleCommand {
        cmd: CreateTracker.type => Try(TimerCreated(EventId(), cmd.id))
      }

      // handle command single Try[List[Event]]
      .handleCommand {
        cmd: CreateAndStartTracking =>
          Try {
            List(
              TimerCreated(EventId(), cmd.id),
              TimerStarted(cmd.taskTitle, OffsetDateTime.now(), EventId(), cmd.id)
            )
          }
      }

      // handle command single Try[immutable.Seq[Event]]
      .handleCommand {
        cmd: CreateAndStartTracking =>
          Try {
            immutable.Seq(
              TimerCreated(EventId(), cmd.id),
              TimerStarted(cmd.taskTitle, OffsetDateTime.now(), EventId(), cmd.id)
            )
          }
      }
      // handle command single Future[Event]
      .handleCommand {
        cmd: CreateTracker.type => Future.successful(TimerCreated(EventId(), cmd.id))
      }

      // handle command single Future[List[Event]]
      .handleCommand {
        cmd: CreateAndStartTracking =>
          Future.successful {
            List(
              TimerCreated(EventId(), cmd.id),
              TimerStarted(cmd.taskTitle, OffsetDateTime.now(), EventId(), cmd.id)
            )
          }
      }

      // handle command single Future[immutable.Seq[Event]]
      .handleCommand {
        cmd: CreateAndStartTracking =>
          Future.successful {
            immutable.Seq(
              TimerCreated(EventId(), cmd.id),
              TimerStarted(cmd.taskTitle, OffsetDateTime.now(), EventId(), cmd.id)
            )
          }
      }
  }
}
