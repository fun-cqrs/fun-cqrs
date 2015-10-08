package io.strongtyped.funcqrs

import java.util.UUID

trait Aggregate {

  type Id <: AggregateID

  type Protocol <: ProtocolDef

  def id: Id

}

/** Base trait for definitions of type-safe aggregate ids */
trait AggregateID {

  def value: String

}

trait AggregateUUID extends AggregateID {

  def uuid: UUID

  final def value = uuid.toString
}