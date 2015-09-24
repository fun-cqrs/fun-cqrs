package fun.cqrs

import java.util.UUID

trait IdentifiableCommand {
  this: DomainCommand =>

  val id: CommandId = CommandId()
}

case class CommandId(value: UUID = UUID.randomUUID())
