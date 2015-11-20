package io.funcqrs

import java.util.UUID

trait DomainEvent {
  def id: EventId
  def commandId: CommandId
}

case class EventId(value: UUID = UUID.randomUUID())