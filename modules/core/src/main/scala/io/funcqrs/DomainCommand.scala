package io.funcqrs

import java.util.UUID

trait DomainCommand {
  val id: CommandId = CommandId()
}

case class CommandId(value: UUID = UUID.randomUUID())

