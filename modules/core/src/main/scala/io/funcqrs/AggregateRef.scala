package io.funcqrs

import io.funcqrs.interpreters.Identity

import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.language.higherKinds
import scala.util.Try

trait AggregateRef[A, C, E, F[_]] {

  def ?(cmd: C): F[immutable.Seq[E]] = ask(cmd)
  def ask(cmd: C): F[immutable.Seq[E]]

  def !(cmd: C): Unit = tell(cmd)
  def tell(cmd: C): Unit

  def state(): F[A]
  def exists(): F[Boolean]

  def withAskTimeout(timeout: FiniteDuration): AggregateRef[A, C, E, Future]
}

trait IdentityAggregateRef[A, C, E] extends AggregateRef[A, C, E, Identity]

trait TryAggregateRef[A, C, E] extends AggregateRef[A, C, E, Try]

trait AsyncAggregateRef[A, C, E] extends AggregateRef[A, C, E, Future] {
  def timeoutDuration: FiniteDuration
}
