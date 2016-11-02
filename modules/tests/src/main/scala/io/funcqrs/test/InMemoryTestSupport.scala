package io.funcqrs.test

import io.funcqrs.backend.QuerySelectAll
import io.funcqrs.test.backend.InMemoryBackend
import io.funcqrs._

import scala.collection.mutable
import scala.concurrent.Future
import scala.reflect.ClassTag
import io.funcqrs.config.api._
import org.slf4j.LoggerFactory

trait InMemoryTestSupport {

  val logger = LoggerFactory.getLogger("InMemoryTestSupport")

  // internal queue with events. All events produced by the testing
  // will be added to this queue for later assertions
  private val receivedEvents = mutable.Queue[DomainEvent]()

  private val internalProjection = new Projection {
    def handleEvent: HandleEvent = {
      case evt =>
        receivedEvents += evt // send all events to queue
        log(evt)
        Future.successful(())
    }
    def log(evt: DomainEvent) = logger.debug(s"received evt: $evt")
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

  private def lastReceivedEvent(): DomainEvent = {
    assert(receivedEvents.nonEmpty, "No events on queue")
    receivedEvents.front
  }

  /**
    * Check if the last received event matches the passed [[PartialFunction]].
    *
    * Useful to verify the content of the event using extractors.
    *
    * @param pf - a PartialFunction from [[DomainEvent]] to [[T]]
    */
  def expectEventPF[E <: DomainEvent, T](pf: PartialFunction[DomainEvent, T]): T = {
    val lastReceived = lastReceivedEvent()
    assert(pf.isDefinedAt(lastReceived), s"PartialFunction is not defined for last received event was: $lastReceived")
    // remove if assertion is passes
    pf(receivedEvents.dequeue)
  }

  /**
    * Check only the type of the head of the test event buffer
    *
    * @tparam E - the event type
    */
  def expectEvent[E <: DomainEvent: ClassTag]: E = {

    val lastReceived = lastReceivedEvent()
    val expectedType = ClassTagImplicits[E].runtimeClass

    assert(
      lastReceived.getClass == expectedType,
      s"Last received event was: $lastReceived, expected of type ${expectedType.getSimpleName}"
    )

    // remove if assertion passes
    receivedEvents.dequeue.asInstanceOf[E]
  }

  def expectEventExists[E <: DomainEvent: ClassTag](check: E => Boolean): E = {

    val lastReceived = lastReceivedEvent()
    val expectedType = ClassTagImplicits[E].runtimeClass

    assert(
      lastReceived.getClass == expectedType,
      s"Last received event was: $lastReceived, expected of type ${expectedType.getSimpleName}"
    )
    assert(
      check(lastReceived.asInstanceOf[E]),
      s"Predicate doesn't hold for $lastReceived"
    )

    // remove if assertion passes
    receivedEvents.dequeue.asInstanceOf[E]
  }

  /**
    * Check only the type of the head of the test event buffer
    *
    * @tparam E - the event type
    */
  @deprecated(message = "Use expectEvent", since = "0.4.7")
  def expectEventType[E <: DomainEvent: ClassTag](): E =
    expectEvent[E]

  /**
    * Check that the internal event buffer is empty meaning that there is no new
    * events to be processed.
    *
    * Works exactly the same as `expectNoMoreEvents`.
    *
    * This method is generally used to verify that a command didn't emitted any event.
    */
  def expectNoEvent(): Unit =
    expectNoMoreEvents()

  /**
    * Check that the internal event buffer is empty meaning that there is no new
    * events to be processed.
    *
    * Works exactly the same as `expectNoMoreEvents`.
    *
    * This method is generally used to the end of a test case to verity that all events emitted
    * during the test were effectively consumed and that no unexpected event were emitted.
    */
  def expectNoMoreEvents(): Unit = {
    val events = receivedEvents.map(_.getClass.getSimpleName).mkString("[", "|", "]")
    assert(receivedEvents.isEmpty, s"Event queue not empty: $events")
  }

  def events: List[DomainEvent] = receivedEvents.toList
}
