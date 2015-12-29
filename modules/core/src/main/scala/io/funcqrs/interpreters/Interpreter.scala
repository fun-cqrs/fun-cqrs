package io.funcqrs.interpreters

import io.funcqrs.behavior.Behavior
import io.funcqrs.interpreters.Monads._
import io.funcqrs.{ AggregateAliases, AggregateLike }

import scala.language.higherKinds

/**
 * Base Interpreter trait.
 *
 * Implementors must define which type F must be bound to.
 */
trait Interpreter[A <: AggregateLike, F[_]] extends AggregateAliases {

  type Aggregate = A

  def behavior: Behavior[A]

  def handleCommand(cmd: Command): F[Events]

  def handleCommand(aggregate: Aggregate, cmd: Command): F[Events]

  def onEvent(evt: Event): A = behavior.onEvent(evt)

  def onEvent(aggregate: A, evt: Event): A = behavior.onEvent(aggregate, evt)

  def applyCommand(cmd: Command)(implicit monadsOps: MonadOps[F]): F[(Events, A)] = {
    monad(handleCommand(cmd)).map { evts =>
      val agg = onEvent(evts.head)
      val finalAgg = evts.tail.foldLeft(agg) { case (currentAgg, evt) => onEvent(currentAgg, evt) }
      (evts, finalAgg)
    }
  }

  def applyCommand(cmd: Command, aggregate: Aggregate)(implicit monadsOps: MonadOps[F]): F[(Events, A)] = {
    monad(handleCommand(aggregate, cmd)).map { evts =>
      val finalAgg = evts.foldLeft(aggregate) { case (acc, evt) => onEvent(acc, evt) }
      (evts, finalAgg)
    }
  }

}