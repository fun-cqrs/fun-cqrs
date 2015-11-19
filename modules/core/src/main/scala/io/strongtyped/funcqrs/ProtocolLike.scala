package io.strongtyped.funcqrs

trait ProtocolLike {
  trait ProtocolCommand extends DomainCommand
  trait ProtocolEvent extends DomainEvent
}
