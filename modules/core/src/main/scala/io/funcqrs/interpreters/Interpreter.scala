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
   * It's responsible for executing the command and lifting the output to F[_]
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
   * Natural transformation from [[Try]] to a [[F]]
   *
   * In the occurrence of missing behavior, we have no other choice than emitting a [[MissingBehaviorException]].
   * This is wrapped in a [[Try]] that must be transformed to the correct error type for F[_]
   *
   * @param any - the produced events
   * @return
   */
  protected def fromTry[B](any: Try[B]): F[B]

  private final def onCommand(state: State[Aggregate], cmd: Command): F[Events] = {

    val tryActions =
      if (behavior.isDefinedAt(state)) {
        Success(behavior(state))
      } else {
        Failure(new MissingBehaviorException(s"No behavior defined for current aggregate state"))
      }

    // produce all events by applying Command
    val tryEvents = tryActions.map { actions =>
      interpret(cmd, actions.onCommand(cmd))
    }

    // Try[F[Events]] -> F[Events]
    tryEvents match {
      case Success(eventsInF) => eventsInF
      case Failure(ex) => fromTry(Failure(ex))
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
  private final def onEvents(state: State[Aggregate], evts: Events): F[State[Aggregate]] = {

    val tried =
      Try { // don't let exceptions leak
        evts.foldLeft(state) {
          case (aggState, evt) => onEvent(aggState, evt)
        }
      }

    fromTry(tried)
  }

  final def applyCommand(state: State[Aggregate], cmd: Command): F[(Events, State[A])] =
    for {
      evts <- onCommand(state, cmd)
      updatedAgg <- onEvents(state, evts)
    } yield (evts, updatedAgg)

}