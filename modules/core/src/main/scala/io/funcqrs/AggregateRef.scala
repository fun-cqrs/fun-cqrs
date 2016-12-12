package io.funcqrs

import io.funcqrs.behavior.api.Types
import io.funcqrs.interpreters.Identity

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.language.higherKinds
import scala.util.Try

trait AggregateRef[A, F[_]] {

  val types: Types[A]

  def ?(cmd: types.Command): F[types.Events] = ask(cmd)
  def ask(cmd: types.Command): F[types.Events]

  def !(cmd: types.Command): Unit = tell(cmd)
  def tell(cmd: types.Command): Unit

  def state(): F[types.Aggregate]
  def exists(): F[Boolean]

  def withAskTimeout(timeout: FiniteDuration): AggregateRef[A, Future]
}

trait IdentityAggregateRef[A] extends AggregateRef[A, Identity]

trait TryAggregateRef[A] extends AggregateRef[A, Try]

trait AsyncAggregateRef[A] extends AggregateRef[A, Future] {
  def timeoutDuration: FiniteDuration
}
