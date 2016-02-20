package io.funcqrs.interpreters

import io.funcqrs.behavior.{ Behavior, Initialized, State }
import io.funcqrs.interpreters.Monads._
import io.funcqrs.{ AggregateAliases, AggregateLike }

import scala.language.higherKinds

/**
 * Base Interpreter trait.
 *
 * Implementors must define which type F must be bound to.
 */
abstract class Interpreter[A <: AggregateLike, F[_]: MonadOps] extends AggregateAliases {

  type Aggregate = A

  def behavior: Behavior[A]

  def handleCommand(state: State[Aggregate], cmd: Command): F[Events]

  def onEvent(state: State[Aggregate], evt: Event): A =
    behavior(state).onEvent(evt)

  def applyCommand(cmd: Command, state: State[Aggregate]): F[(Events, State[A])] = {
    monad(handleCommand(state, cmd)).map { evts: Events =>
      val newState = evts.foldLeft(state) {
        case (aggregateState, evt) => Initialized(onEvent(aggregateState, evt))
      }
      (evts, newState)
    }
  }

}