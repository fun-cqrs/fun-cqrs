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

  def handleCommand(optionalAggregate: Option[Aggregate], cmd: Command): F[Events]

  def onEvent(optionalAggregate: Option[Aggregate], evt: Event): A = behavior.onEvent(optionalAggregate, evt)

  def applyCommand(cmd: Command, optionalAggregate: Option[Aggregate])(implicit monadsOps: MonadOps[F]): F[(Events, Option[A])] = {
    monad(handleCommand(optionalAggregate, cmd)).map { evts: Events =>
      val optionalAgg = evts.foldLeft(optionalAggregate) {
        case (optionalAggregate, evt) =>
          Some(onEvent(optionalAggregate, evt))
      }
      (evts, optionalAgg)
    }
  }

}