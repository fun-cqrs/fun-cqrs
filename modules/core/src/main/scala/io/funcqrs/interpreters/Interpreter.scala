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
   * Lift a [[Try]] of [[Events]] to a [[F]] of [[Events]]
   *
   * Internally, interpreters will check if all emitted events can be applied. In other words, it checks
   * if event handlers were defined for each emitted event for each state transition.
   *
   * In the occurrence of missing event handlers, we have no other choice than emitting a [[MissingEventHandlerException]].
   * This is wrapped in a [[Try]] that must be lift to F[_]
   *
   * @param events - the produced events
   * @return
   */
  protected def fromTry(events: Try[Events]): F[Events]

  final def onCommand(state: State[Aggregate], cmd: Command): F[Events] = {

    val actionsTry =
      Try(behavior(state)).recoverWith {
        case _: MatchError =>
          Failure(new MissingBehaviorException(s"No behavior defined for current aggregate state"))
      }

    val result = actionsTry.map { actions =>
      // produce all events by apply Command
      val events = interpret(cmd, actions.onCommand(cmd))

      events.flatMap { evts =>
        fromTry(tryHandleAllEvents(state, evts))
      }
    }

    // Try[F[Events]]
    result match {
      case Success(eventsInF) => eventsInF
      case Failure(ex) => fromTry(Failure(ex))
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
  final def onEvent(state: State[Aggregate], evt: Event): State[Aggregate] =
    Initialized(behavior(state).onEvent(evt))

  /**
   * Apply all 'evts' on passed 'state'.
   *
   * @param state - the aggregate current state
   * @param evts - events to be applied
   * @throws MissingEventHandlerException if no Event handler is defined for one of the passed events.
   * @return new aggregate state after applying all events
   */
  final def onEvents(state: State[Aggregate], evts: Events): State[Aggregate] = {
    evts.foldLeft(state) {
      case (aggState, evt) => onEvent(aggState, evt)
    }
  }

  final def applyCommand(cmd: Command, state: State[Aggregate]): F[(Events, State[A])] = {
    onCommand(state, cmd).map { evts: Events =>
      (evts, onEvents(state, evts))
    }
  }

}