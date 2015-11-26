package io.funcqrs

import scala.collection.immutable

trait ProtocolLike {
  trait ProtocolCommand extends DomainCommand
  trait ProtocolEvent extends DomainEvent
}

trait ProtocolAliases {

  type Protocol <: ProtocolLike

  /** Alias for Aggregate's ProtocolCommand */
  type Command = Protocol#ProtocolCommand

  /** Alias for Aggregate's ProtocolEvent */
  type Event = Protocol#ProtocolEvent

  /** Alias for an immutable Seq of Aggregate's ProtocolEvent */
  type Events = immutable.Seq[Event]

}