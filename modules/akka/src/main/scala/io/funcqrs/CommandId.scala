package io.funcqrs

import java.util.UUID

case class CommandId(value: UUID = UUID.randomUUID())

trait CommandIdFacet {
  def id: CommandId
}
