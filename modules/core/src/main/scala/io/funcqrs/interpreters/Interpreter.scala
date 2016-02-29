package io.funcqrs.interpreters

import io.funcqrs.behavior.{ Behavior, Initialized, State }
import io.funcqrs.interpreters.Monads._
import io.funcqrs.{ CommandException, AggregateAliases, AggregateLike }

import scala.concurrent.Future
import scala.language.higherKinds

/**
 * Base Interpreter trait.
 *
 * Implementors must define which type F must be bound to.
 */
abstract class Interpreter[A <: AggregateLike, F[_]: MonadOps] extends AggregateAliases {

  type Aggregate = A

  def behavior: Behavior[A]

  def onCommand(state: State[Aggregate], cmd: Command): F[Events]

  def onEvent(state: State[Aggregate], evt: Event): A =
    behavior(state).onEvent(evt)

  final protected def eventHandlerNotDefined(state: State[Aggregate], evts: Events) = {
    // collect events with listener
    val badEventsNames = evts.collect {
      case e if !behavior(state).canHandleEvent(e) => e.getClass.getSimpleName
    }
    new CommandException(s"No event handlers defined for events: ${badEventsNames.mkString(",")}")
  }

  def applyCommand(cmd: Command, state: State[Aggregate]): F[(Events, State[A])] = {
    monad(onCommand(state, cmd)).map { evts: Events =>
      val newState = evts.foldLeft(state) {
        case (aggregateState, evt) => Initialized(onEvent(aggregateState, evt))
      }
      (evts, newState)
    }
  }

}