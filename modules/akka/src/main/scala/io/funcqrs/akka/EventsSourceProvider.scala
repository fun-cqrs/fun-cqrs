package io.funcqrs.akka

import akka.NotUsed
import akka.actor.ActorContext
import akka.persistence.query.EventEnvelope
import akka.stream.scaladsl.Source

/**
 * Provides an Akka-Streams [[Source]] that produces [[EventEnvelope]]s.
 * TODO: document it with implementation example
 */
trait EventsSourceProvider {

  def source(offset: Long)(implicit context: ActorContext): Source[EventEnvelope, NotUsed]

}

