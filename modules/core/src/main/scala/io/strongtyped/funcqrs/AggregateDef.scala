package io.strongtyped.funcqrs

import java.util.UUID

import scala.collection.immutable

trait AggregateDef {

  type Id <: AggregateID

  type Protocol <: ProtocolDef

  def id: Id

}

trait AggregateAliases {

  /** The Aggregate type.
    * This is the only (abstract) type member to be defined.
    *
    * All other type members are aliases defined by type projection of inner types from Aggregate type itself.
    */
  type Aggregate <: AggregateDef

  /** Alias for Aggregate#Id */
  type Id = Aggregate#Id

  /** Alias for Aggregate#Protocol */
  type Protocol = Aggregate#Protocol

  /** Alias for Aggregate's ProtocolCommand */
  type Command = Protocol#ProtocolCommand

  /** Alias for Aggregate's ProtocolEvent */
  type Event = Protocol#ProtocolEvent

  /** Alias for an immutable Seq of Aggregate's ProtocolEvent */
  type Events = immutable.Seq[Event]
}

/** Base trait for definitions of type-safe aggregate ids */
trait AggregateID {

  def value: String

}

trait AggregateUUID extends AggregateID {

  def uuid: UUID

  final def value = uuid.toString
}