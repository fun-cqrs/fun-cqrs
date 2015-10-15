package io.strongtyped.funcqrs.akka

import akka.actor.Actor
import akka.stream.scaladsl.Source
import io.strongtyped.funcqrs.DomainEvent

/**
 * Provides an Akka-Streams [[Source]] that produces [[DomainEvent]]s.
 */
trait EventsSourceProvider {
  this: Actor =>

  def source(offset:Long): Source[DomainEvent, Unit]
}
