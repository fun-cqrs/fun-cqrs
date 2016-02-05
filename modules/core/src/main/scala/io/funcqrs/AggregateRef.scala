package io.funcqrs

import io.funcqrs.interpreters.Identity
import scala.concurrent.Future
import scala.language.higherKinds
import scala.util.Try

trait AggregateRef[A <: AggregateLike, +F[_]] extends AggregateAliases {

  type Aggregate = A

  def ?(cmd: Command): F[Events] = ask(cmd)
  def ask(cmd: Command): F[Events]

  def !(cmd: Command): Unit = tell(cmd)
  def tell(cmd: Command): Unit

  def state(): F[Aggregate]
  def exists(): F[Boolean]
}

trait IdentityAggregateRef[A <: AggregateLike] extends AggregateRef[A, Identity]
trait TryAggregateRef[A <: AggregateLike] extends AggregateRef[A, Try]
trait AsyncAggregateRef[A <: AggregateLike] extends AggregateRef[A, Future]