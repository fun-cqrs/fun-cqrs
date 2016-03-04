package io.funcqrs.test

import io.funcqrs.backend.QuerySelectAll
import io.funcqrs.test.backend.InMemoryBackend
import io.funcqrs._
import scala.collection.mutable
import scala.concurrent.Future
import scala.reflect.ClassTag
import io.funcqrs.config.api._

trait InMemoryTestSupport {

  // internal queue with events. All events produced by the testing
  // will be added to this queue for later assertions
  private val receivedEvents = mutable.Queue[DomainEvent]()

  private val internalProjection = new Projection {
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
        query = QuerySelectAll,
        projection = internalProjection,
        name = "InternalTestProjection"
      )
    }
    backend
  }

  def configure(backend: InMemoryBackend): Unit

  def aggregateRef[A <: AggregateLike: ClassTag](id: A#Id): IdentityAggregateRef[A] = {
    backend.aggregateRef[A](id)
  }

  def expectEvent[T](pf: PartialFunction[DomainEvent, T]): T = {
    val receivedEvent = receivedEvents.dequeue
    expectEvent(receivedEvent, pf)
  }

  def expectEventType[E <: DomainEvent: ClassTag]: E = {
    val receivedEvent = receivedEvents.dequeue
    val pf: PartialFunction[DomainEvent, E] = {
      case anyEvent if anyEvent.getClass == ClassTagImplicits[E].runtimeClass =>
        anyEvent.asInstanceOf[E]
    }
    expectEvent(receivedEvent, pf)
  }

  private def expectEvent[T](evt: DomainEvent, pf: PartialFunction[DomainEvent, T]): T = {
    assert(pf.isDefinedAt(evt), s"$evt does not match expected Event")
    pf(evt)
  }

  def expectNoEvent() = assert(receivedEvents.isEmpty, "There are events on the queue")
}
