package io.funcqrs

import java.util.UUID

trait DomainEvent

case class EventId(value: UUID = UUID.randomUUID())

trait EventIdFacet {
  def id: EventId
}

trait EventWithCommandId { this: DomainEvent =>
  def commandId: CommandId
}
