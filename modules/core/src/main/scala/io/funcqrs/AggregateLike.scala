package io.funcqrs

import java.util.UUID

/**
  * Base trait for Aggregates
  */
trait AggregateLike extends ProtocolAliases {

  type Id <: AggregateId

  def id: Id

}

trait AggregateAliases extends ProtocolAliases {

  /**
    * The Aggregate type.
    * This is the only (abstract) type member to be defined.
    *
    * All other type members are aliases defined by type projection of inner types from Aggregate type itself.
    */
  type Aggregate <: AggregateLike

  /** Alias for Aggregate#Id */
  type Id = Aggregate#Id

  /** Alias for Aggregate#Protocol */
  type Protocol = Aggregate#Protocol

}

/** Base trait for definitions of type-safe aggregate ids */
trait AggregateId {

  def value: String

}

trait AggregateUUID extends AggregateId {

  def uuid: UUID

  final def value = uuid.toString
}
