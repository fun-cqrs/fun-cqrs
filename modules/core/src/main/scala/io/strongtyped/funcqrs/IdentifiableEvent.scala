package io.strongtyped.funcqrs

import java.util.UUID

trait IdentifiableEvent {
  def id: EventId
}

case class EventId(value: UUID = UUID.randomUUID())