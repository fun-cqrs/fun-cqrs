package io.strongtyped.funcqrs

trait TestDomainEvent extends DomainEvent {

  val id = EventId()
  val commandId = CommandId()
}
