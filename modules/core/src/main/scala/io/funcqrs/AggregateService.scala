package io.funcqrs

import scala.language.higherKinds

trait AggregateService[A <: AggregateLike, +F[_]] extends AggregateAliases {

  type Aggregate = A

  def update(id: Id)(cmd: Command): F[Events]

  def state(id:Id): F[Aggregate]
  def exists(id: Id): F[Boolean]
}


trait AggregateServiceWithAssignedId[A <: AggregateLike, +F[_]] extends AggregateService[A, F] {
  def newInstance(id: Id, cmd: Command): F[Events]
}

trait AggregateServiceWithManagedId[A <: AggregateLike, +F[_]] extends AggregateService[A, F] {
  def newInstance(cmd: Command): F[Events]
}