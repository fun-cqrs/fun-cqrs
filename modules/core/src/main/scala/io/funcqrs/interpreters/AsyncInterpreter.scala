package io.funcqrs.interpreters

import io.funcqrs.AggregateLike
import io.funcqrs.behavior._

import scala.concurrent.Future
import scala.language.higherKinds
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * An Interpreter with F[_] bounded to [[Future]].
 *
 * All command handling are interpreted to [[Future]] of Events.
 *
 * @param behavior - a Aggregate [[Behavior]]
 * @tparam A - an Aggregate type
 */
class AsyncInterpreter[A <: AggregateLike](val behavior: Behavior[A]) extends Interpreter[A, Future] {

  def onCommand(state: State[A], cmd: Command): Future[Events] = {

    val currentActions = behavior(state)
    val events =
      currentActions.onCommand(cmd) match {
        case IdCommandHandlerInvoker(handler) => Future.successful(handler(cmd))
        case TryCommandHandlerInvoker(handler) => Future.fromTry(handler(cmd))
        case FutureCommandHandlerInvoker(handler) => handler(cmd)
      }

    events.flatMap { evts =>
      if (currentActions.canHandleEvents(evts)) Future.successful(evts)
      else Future.failed(eventHandlerNotDefined(state, evts))
    }
  }
}

object AsyncInterpreter {
  def apply[A <: AggregateLike](behavior: Behavior[A]) = new AsyncInterpreter(behavior)
}