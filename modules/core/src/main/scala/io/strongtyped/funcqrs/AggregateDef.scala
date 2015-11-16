package io.strongtyped.funcqrs

import java.util.UUID

import scala.collection.immutable

trait AggregateDef {

  type Id <: AggregateID

  type Protocol <: ProtocolDef

  def id: Id

}

trait AggregateTypes {
  type Aggregate <: AggregateDef
  type Id = Aggregate#Id
  type Protocol = Aggregate#Protocol
  type Command = Protocol#ProtocolCommand
  type Event = Protocol#ProtocolEvent
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