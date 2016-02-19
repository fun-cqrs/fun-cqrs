package io.funcqrs.akka

import akka.NotUsed
import akka.persistence.query.EventEnvelope
import akka.stream.scaladsl.Source

/** Provides an Akka-Streams [[Source]] that produces [[EventEnvelope]]s.
  */
trait EventsSourceProvider {

  def source(offset: Long): Source[EventEnvelope, NotUsed]

}
