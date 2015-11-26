package io.funcqrs

trait TestDomainEvent extends DomainEvent {

  val id = EventId()
  val commandId = CommandId()
}
