package io.strongtyped.funcqrs

trait ProtocolDef {
  trait ProtocolCommand extends DomainCommand
  trait ProtocolEvent extends DomainEvent
}
