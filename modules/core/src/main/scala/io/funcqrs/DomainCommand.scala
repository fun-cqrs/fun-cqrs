package io.funcqrs

import java.util.UUID

trait DomainCommand

case class CommandId(value: UUID = UUID.randomUUID())

trait CommandIdFacet {
  def id: CommandId
}
