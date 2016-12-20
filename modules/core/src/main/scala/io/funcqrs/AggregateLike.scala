package io.funcqrs

import java.util.UUID

/**
  * Base trait for Aggregates
  */
trait AggregateLike {

  type Id <: AggregateId

  def id: Id

}

/** Base trait for definitions of type-safe aggregate ids */
trait AggregateId {

  def value: String

}

trait AggregateUUID extends AggregateId {

  def uuid: UUID

  final def value = uuid.toString
}
