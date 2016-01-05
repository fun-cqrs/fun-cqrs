package io.funcqrs.backend

import io.funcqrs._

import scala.language.higherKinds

trait Backend {

  type F[_]

  def configureProjection(config: ProjectionConfig): Unit

  def configureAggregate[A <: AggregateLike](config: AggregateConfigWithAssignedId[A]): AggregateServiceWithAssignedId[A, F]

  def configureAggregate[A <: AggregateLike](config: AggregateConfigWithManagedId[A]): AggregateServiceWithManagedId[A, F]
}
