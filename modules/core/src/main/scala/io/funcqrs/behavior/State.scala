package io.funcqrs.behavior

import io.funcqrs.AggregateLike

sealed trait State[+A <: AggregateLike] {
  def aggregateId: A#Id

  def isInitialized: Boolean
}

case class Uninitialized[A <: AggregateLike](aggregateId: A#Id) extends State[A] {
  val isInitialized: Boolean = false
}

case class Initialized[A <: AggregateLike](aggregate: A) extends State[A] {
  val aggregateId = aggregate.id
  val isInitialized: Boolean = true
}
