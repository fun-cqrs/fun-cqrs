package io.funcqrs

import java.util.UUID

case class EventId(value: UUID = UUID.randomUUID())

trait EventIdFacet {
  def id: EventId
}

trait EventWithCommandId {
  def commandId: CommandId
}
