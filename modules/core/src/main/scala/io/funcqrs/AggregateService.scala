package io.funcqrs

import io.funcqrs.interpreters.Identity

import scala.concurrent.Future
import scala.language.higherKinds
import scala.util.Try

trait AggregateService[A <: AggregateLike, +F[_]] extends AggregateAliases {

  type Aggregate = A

  def update(id: Id)(cmd: Command): F[Events]

  def newInstance(id: Id, cmd: Command): F[Events]
  def newInstance(cmd: Command): F[Events]

  def state(id: Id): F[Aggregate]
  def exists(id: Id): F[Boolean]

}

trait IdentityAggregateService[A <: AggregateLike] extends AggregateService[A, Identity]
trait TryAggregateService[A <: AggregateLike] extends AggregateService[A, Try]
trait AsyncAggregateService[A <: AggregateLike] extends AggregateService[A, Future]