package io.funcqrs.test

import io.funcqrs.{ Projection, _ }
import io.funcqrs.backend.QuerySelectAll
import io.funcqrs.config.api._
import io.funcqrs.test.backend.InMemoryBackend

import scala.collection.mutable
import scala.concurrent.Future
import scala.reflect.ClassTag
import scala.util.{ Failure, Success, Try }

trait InMemoryTestSupport {

  // internal queue with events. All events produced by the testing
  // will be added to this queue for later assertions
  private lazy val receivedEvents = mutable.Queue[Any]()

  private lazy val internalProjection = new Projection {
    def handleEvent: HandleEvent = {
      case event =>
        receivedEvents += event // add all events to queue
        Future.successful(())
    }
  }

  lazy val backend = {
    val backend = new InMemoryBackend
    configure(backend)

    backend.configure {
      projection(
        query      = QuerySelectAll,
        projection = internalProjection,
        name       = "InternalTestProjection"
      )
    }
    backend
  }

  /**
    * Implement this method to configure the passed [[InMemoryBackend]]
    * for your tests
    */
  def configure(backend: InMemoryBackend): Unit

  private def oldestEvent(): Any = {
    bufferShouldNoBeEmpty()
    receivedEvents.front
  }

  private def bufferShouldNoBeEmpty() = {
    assert(receivedEvents.nonEmpty, "No events on queue")
  }

  /**
    * Check only the type of the head of the test event buffer
    * @tparam E - the event type
    * @throws AssertionError in Event Buffer head doesn't match passed Event
    * @return E if head of event buffer is E
    */
  def expectEvent[E: ClassTag]: E = {

    val headOfBuffer = oldestEvent()
    val expectedType = ClassTagImplicits[E].runtimeClass

    assert(
      headOfBuffer.getClass == expectedType,
      s"Found: $headOfBuffer, expected of type ${expectedType.getSimpleName}"
    )

    // remove if assertion passes
    receivedEvents.dequeue.asInstanceOf[E]
  }

  /**
    * Check if the next event in buffer matches the passed [[PartialFunction]].
    *
    * Useful to verify the content of the event pattern matching.
    *
    * @param pf - a PartialFunction from [[Any]] to [[T]]
    */
  def expectEventPF[T](pf: PartialFunction[Any, T]): T = {
    val lastReceived = oldestEvent()
    assert(pf.isDefinedAt(lastReceived), s"PartialFunction is not defined for next buffer event, was: $lastReceived")
    // remove if assertion passes
    receivedEvents.dequeue
    // apply PF to it
    pf(lastReceived)
  }

  /**
    * Search event buffer for `E` consuming all previous Events.
    *
    * @tparam E - the event type
    * @throws AssertionError in case there is no matching Event on the buffer
    * @return E if event buffer contains an Event of type E
    */
  def lookupExpectedEvent[E: ClassTag]: E = {
    Try(expectEvent[E]) match {
      case Success(event) => event
      case Failure(ignore) =>
        receivedEvents.dequeue()
        lookupExpectedEvent[E]
    }
  }

  /**
    * Search event buffer for an `Event` matching the passed [[PartialFunction]] consuming all previous Events.
    *
    * Useful to verify the content of the event pattern matching.
    *
    * @param pf - a PartialFunction from [[Any]] to [[T]]
    * @throws AssertionError in case there is no matching Event on the buffer
    * @return E if event buffer contains an Event of type E
    */
  def lookupExpectedEventPF[T](pf: PartialFunction[Any, T]): T = {
    Try(expectEventPF(pf)) match {
      case Success(event) => event
      case Failure(ignore) =>
        receivedEvents.dequeue()
        lookupExpectedEventPF(pf)
    }
  }

  def lastReceivedEvent[E: ClassTag]: E = {

    bufferShouldNoBeEmpty()

    val lastReceived = receivedEvents.toList.last
    val expectedType = ClassTagImplicits[E].runtimeClass

    assert(
      lastReceived.getClass == expectedType,
      s"Last received event was: $lastReceived, expected of type ${expectedType.getSimpleName}"
    )

    lastReceived.asInstanceOf[E]
  }

  def lastReceivedEventPF[T](pf: PartialFunction[Any, T]): T = {

    bufferShouldNoBeEmpty()

    val lastReceived = receivedEvents.toList.last

    assert(pf.isDefinedAt(lastReceived), s"PartialFunction is not defined for last received event, was: $lastReceived")
    pf(lastReceived)
  }

  /**
    * Check that the internal event buffer is empty meaning that there is no new
    * events to be processed.
    *
    * Works exactly the same as `expectNoMoreEvents`.
    *
    * This method is generally used to verify that a command didn't emit any event.
    */
  def expectNoEvent(): Unit =
    expectNoMoreEvents()

  /**
    * Check that the internal event buffer is empty meaning that there is no new
    * events to be processed.
    *
    * Works exactly the same as `expectNoMoreEvents`.
    *
    * This method is generally used at the end of a test case to verity that all events emitted
    * during the test were effectively consumed and that no unexpected event were emitted.
    */
  def expectNoMoreEvents(): Unit = {
    val events = receivedEvents.map(_.getClass.getSimpleName).mkString("[", "|", "]")
    assert(receivedEvents.isEmpty, s"Event queue not empty: $events")
  }

  def events: List[Any] = receivedEvents.toList

  /**
    * Drop all Events.
    * This method is useful to make sure there are no
    * unconsumed event in the queue before starting a new test
    *
    * Useful when reusing the same test support on multiple test
    */
  def dropAllEvents() = {
    receivedEvents.clear()
  }
}
