package io.funcqrs.interpreters

import io.funcqrs.AggregateLike
import io.funcqrs.behavior._

import scala.concurrent.{ Future, Await }
import scala.concurrent.duration.{ Duration, _ }

/**
 * An Interpreter with F[_] bounded to [[Identity]].
 *
 * All command handling are interpreted to [[Identity]] of Events (ie: the pure value).
 *
 * Will block on any async operation defined by [[Behavior]].
 *
 * This interpreter should be used for testing and / or for behaviors that preferably don't define any async operation.
 *
 * @param behavior - a Aggregate [[Behavior]]
 * @param atMost - the maximum duration we are to wait before Futures timeout.
 * @tparam A - an Aggregate type
 */
class IdentityInterpreter[A <: AggregateLike](val behavior: Behavior[A], atMost: Duration = 5.seconds) extends Interpreter[A, Identity] {

  def onCommand(state: State[A], cmd: Command): Identity[Events] = {

    val currentActions = behavior(state)
    val events =
      currentActions.onCommand(cmd) match {
        case IdCommandHandlerInvoker(handler) => handler(cmd)
        case TryCommandHandlerInvoker(handler) => handler(cmd).get
        case FutureCommandHandlerInvoker(handler) => Await.result(handler(cmd), atMost)
      }

    if (currentActions.canHandleEvents(events)) events
    else throw eventHandlerNotDefined(state, events)
  }
}

object IdentityInterpreter {
  def apply[A <: AggregateLike](behavior: Behavior[A], atMost: Duration = 5.seconds) = new IdentityInterpreter(behavior, atMost)
}