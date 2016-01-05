package io.funcqrs.backend

import org.reactivestreams.Publisher

trait EventsPublisherProvider {
  def publisher(offset: Long): Publisher[EventEnvelope]
}

case class EventEnvelope(offset: Long,
                          sequenceNr: Long,
                          event: Any)