package io.funcqrs.interpreters

import io.funcqrs._
import io.funcqrs.behavior._
import io.funcqrs.interpreters.Monads._

import scala.language.higherKinds
import scala.util.{ Failure, Success, Try }

/**
 * Base Interpreter trait.
 *
 * Implementors must define which type F must be bound to.
 */
abstract class Interpreter[A <: AggregateLike, F[_]: MonadOps] extends AggregateAliases {

  type Aggregate = A

  protected def behavior: Behavior[A]

  type InterpreterFunction = PartialFunction[(Command, CommandHandlerInvoker[Command, Event]), F[Events]]

  /**
   * The interpret PartialFunction is specific to each interpreter.
   * It's responsible for execution the command and lifting the output
   * to F[_]
   *
   * {{{ // example of interpret for an AsyncInterpreter
   *    def interpret: InterpreterFunction = {
   *      case (cmd, IdCommandHandlerInvoker(handler))      => Future.successful(handler(cmd))
   *      case (cmd, TryCommandHandlerInvoker(handler))     => Future.fromTry(handler(cmd))
   *      case (cmd, FutureCommandHandlerInvoker(handler))  => handler(cmd)
   *    }
   * }}}
   *
   * @return
   */
  protected def interpret: InterpreterFunction

  /**
   * @param ex - the exception emitted when handling the command
   * @return
   */
  protected def onCommandFailure(ex: Throwable): F[Events]

  private final def onCommand(state: State[Aggregate], cmd: Command): F[Events] = {

    val actionsTry =
      Try(behavior(state)).recoverWith {
        case _: MatchError =>
          Failure(new MissingBehaviorException(s"No behavior defined for current aggregate state"))
      }

    // produce all events by applying Command
    val events = actionsTry.map { actions =>
      interpret(cmd, actions.onCommand(cmd))
    }

    // Try[F[Events]] -> F[Events]
    events match {
      case Success(eventsInF) => eventsInF
      case Failure(ex) => onCommandFailure(ex)
    }
  }

  private final def tryHandleAllEvents(state: State[Aggregate], events: Events): Try[Events] = {
    // apply all emitted events to aggregate state to verify that
    // we have event handlers for them all
    Try(onEvents(state, events))
      .map(_ => events) // if successful, return a Success(events)
      .recoverWith {
        case _: MatchError => Failure(new MissingBehaviorException(s"No behavior defined for current aggregate state"))
      }
  }
  /**
   * Apply all 'evt' on passed 'state'.
   *
   * @param state - the aggregate current state
   * @param evt - event to be applied
   * @throws MissingEventHandlerException if no Event handler is defined for the passed event.
   * @return new aggregate state after applying event
   */
  final def onEvent(state: State[Aggregate], evt: Event): State[Aggregate] = {
    if (behavior.isDefinedAt(state)) {
      Initialized(behavior(state).onEvent(evt))
    } else {
      throw new MissingBehaviorException(s"No behavior defined for current aggregate state")
    }
  }

  /**
   * Apply all 'evts' on passed 'state'.
   *
   * @param state - the aggregate current state
   * @param evts - events to be applied
   * @throws MissingEventHandlerException if no Event handler is defined for one of the passed events.
   * @return new aggregate state after applying all events
   */
  private final def onEvents(state: State[Aggregate], evts: Events): State[Aggregate] = {
    evts.foldLeft(state) {
      case (aggState, evt) => onEvent(aggState, evt)
    }
  }

  final def applyCommand(state: State[Aggregate], cmd: Command): F[(Events, State[A])] = {
    onCommand(state, cmd).map { evts: Events =>
      (evts, onEvents(state, evts))
    }
  }

}