package io.funcqrs.behavior

import io.funcqrs.AggregateLike

@deprecated(message = "Obsolete as we don't expose it anymore. Replaced by Option", since = "1.0.0")
sealed trait State[+A <: AggregateLike] {
  def aggregateId: A#Id

  def isInitialized: Boolean
}
@deprecated(message = "Obsolete as we don't expose it anymore. Replaced by None", since = "1.0.0")
case class Uninitialized[A <: AggregateLike](aggregateId: A#Id) extends State[A] {
  val isInitialized: Boolean = false
}

@deprecated(message = "Obsolete as we don't expose it anymore. Replaced by Some", since = "1.0.0")
case class Initialized[A <: AggregateLike](aggregate: A) extends State[A] {
  val aggregateId            = aggregate.id
  val isInitialized: Boolean = true

  override def toString: String = {
    // don't toString the entire aggregate, might be too big
    s"Initialized(aggregateId=$aggregateId)"
  }
}
