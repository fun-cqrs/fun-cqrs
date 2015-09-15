package fun.cqrs

import java.util.UUID

trait Aggregate {

  type Identifier <: AggregateIdentifier

  type Protocol <: ProtocolDef.Commands with ProtocolDef.Events

  def identifier: Identifier

}

/** Base trait for definitions of type-safe aggregate ids */
trait AggregateIdentifier {

  def value: String
}

trait AggregateUUID extends AggregateIdentifier {
  def uuid: UUID

  final def value = uuid.toString
}