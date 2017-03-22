package io.funcqrs

import java.util.UUID

/** Base trait for definitions of type-safe aggregate ids */
trait AggregateId {

  def value: String

}

trait AggregateUUID extends AggregateId {

  def uuid: UUID

  final def value = uuid.toString
}
