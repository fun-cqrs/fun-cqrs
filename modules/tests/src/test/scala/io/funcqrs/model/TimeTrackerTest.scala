package io.funcqrs.model

import io.funcqrs.test.InMemoryTestSupport
import io.funcqrs.test.backend.InMemoryBackend
import org.scalatest.{ FlatSpec, Matchers }
import TimerTrackerProtocol._
import io.funcqrs.config.Api._

class TimeTrackerTest extends FlatSpec with Matchers {

  behavior of "TimerTracker"

  class InMemoryTest extends InMemoryTestSupport {

    def configure(backend: InMemoryBackend): Unit = {
      backend.configure {
        aggregate[TimeTracker](TimeTracker.behavior)
      }
    }

    def trackerRef(id: TrackerId = TrackerId.generate) = aggregateRef[TimeTracker](id)
  }

  it should "create a tracker in idle state" in
    new InMemoryTest {

      val tracker = trackerRef()
      tracker ! CreateTracker

      expectEventType[TimerCreated]

      tracker.state().isIdle shouldBe true
    }

  it should "create a tracker in 'busy' state when sending 'create and start' command" in
    new InMemoryTest {

      val tracker = trackerRef()
      tracker ! CreateAndStartTracking("foo")

      expectEventType[TimerCreated]
      expectEventType[TimerStarted]

      tracker.state().isBusy shouldBe true
    }

  it should "stop current task and add a new one when receive a Replace command" in
    new InMemoryTest {

      val tracker = trackerRef()
      tracker ! CreateAndStartTracking("foo")
      tracker ! ReplaceTask("bar")

      expectEventType[TimerCreated]
      expectEventType[TimerStarted]
      expectEventType[TimerStopped]
      expectEventType[TimerStarted]

      tracker.state().isBusy shouldBe true
      tracker.state().previousTasks should have size 1
    }

  it should "add a new task when receive a Replace command event if not busy" in
    new InMemoryTest {

      val tracker = trackerRef()
      tracker ! CreateTracker
      tracker ! ReplaceTask("foo")

      expectEventType[TimerCreated]
      expectEventType[TimerStarted]

      tracker.state().isBusy shouldBe true
      tracker.state().previousTasks should have size 0
    }
}
