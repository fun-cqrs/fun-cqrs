package io.strongtyped.funcqrs.akka

import akka.actor.Actor
import akka.persistence.query.EventEnvelope
import akka.stream.scaladsl.Source

/**
 * Provides an Akka-Streams [[Source]] that produces [[EventEnvelope]]s.
 */
trait EventsSourceProvider {
  this: Actor =>

  def source(offset: Long): Source[EventEnvelope, Unit]
}
