package io.funcqrs.test

import io.funcqrs.backend.QuerySelectAll
import io.funcqrs.test.backend.InMemoryBackend
import io.funcqrs._

import scala.collection.mutable
import scala.concurrent.Future
import scala.reflect.ClassTag
import io.funcqrs.config.api._

import scala.util.{ Failure, Success, Try }

trait InMemoryTestSupport {

  // internal queue with events. All events produced by the testing
  // will be added to this queue for later assertions
  private lazy val receivedEvents = mutable.Queue[DomainEvent]()

  private lazy val internalProjection = new Projection {
    def handleEvent: HandleEvent = {
      case evt =>
        receivedEvents += evt // send all events to queue
        Future.successful(())
    }
  }

  private lazy val backend = {
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

  def aggregateRef[A <: AggregateLike: ClassTag](id: A#Id): IdentityAggregateRef[A] = {
    backend.aggregateRef[A](id)
  }

  private def oldestEvent(): DomainEvent = {
    bufferShouldNoBeEmpty()
    receivedEvents.front
  }

  private def bufferShouldNoBeEmpty() = {
    assert(receivedEvents.nonEmpty, "No events on queue")
  }

  /**
    * Check only the type of the head of the test event buffer
    *
    * @tparam E - the event type
    * @throws AssertionError in Event Buffer head doesn't match passed Event
    * @return E if head of event buffer is E
    */
  def expectEvent[E <: DomainEvent: ClassTag]: E = {

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
    * @param pf - a PartialFunction from [[DomainEvent]] to [[T]]
    */
  def expectEventPF[E <: DomainEvent, T](pf: PartialFunction[DomainEvent, T]): T = {
    val lastReceived = oldestEvent()
    assert(pf.isDefinedAt(lastReceived), s"PartialFunction is not defined for next buffer event was: $lastReceived")
    // remove if assertion is passes
    pf(receivedEvents.dequeue)
  }

  /**
    * Search event buffer for `E` consuming all previous Events.
    *
    * @tparam E - the event type
    * @throws AssertionError in case there is no matching Event on the buffer
    * @return E if event buffer contains an Event of type E
    */
  def lookupExpectedEvent[E <: DomainEvent: ClassTag]: E = {
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
    * @param pf - a PartialFunction from [[DomainEvent]] to [[T]]
    * @tparam E - the event type
    * @throws AssertionError in case there is no matching Event on the buffer
    * @return E if event buffer contains an Event of type E
    */
  def lookupExpectedEventPF[E <: DomainEvent, T](pf: PartialFunction[DomainEvent, T]): T = {
    Try(expectEventPF(pf)) match {
      case Success(event) => event
      case Failure(ignore) =>
        receivedEvents.dequeue()
        lookupExpectedEventPF(pf)
    }
  }

  def lastReceivedEvent[E <: DomainEvent: ClassTag]: E = {

    bufferShouldNoBeEmpty()

    val lastReceived = receivedEvents.toList.last
    val expectedType = ClassTagImplicits[E].runtimeClass

    assert(
      lastReceived.getClass == expectedType,
      s"Last received event was: $lastReceived, expected of type ${expectedType.getSimpleName}"
    )

    lastReceived.asInstanceOf[E]
  }

  def lastReceivedEventPF[E <: DomainEvent, T](pf: PartialFunction[DomainEvent, T]): T = {

    bufferShouldNoBeEmpty()

    val lastReceived = receivedEvents.toList.last

    assert(pf.isDefinedAt(lastReceived), s"PartialFunction is not defined for last received event was: $lastReceived")
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

  def events: List[DomainEvent] = receivedEvents.toList
}
