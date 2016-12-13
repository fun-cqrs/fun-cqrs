package io.funcqrs.interpreters

import io.funcqrs.AggregateLike
import io.funcqrs.behavior._

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration.{ Duration, _ }
import scala.util.Try

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

  protected def interpret: InterpreterFunction = {
    case (cmd, IdCommandHandlerInvoker(handler))     => handler(cmd)
    case (cmd, TryCommandHandlerInvoker(handler))    => handler(cmd).get
    case (cmd, FutureCommandHandlerInvoker(handler)) => Await.result(handler(cmd), atMost)
  }

  protected def fromTry[B](any: Try[B]): Identity[B] =
    any.get // yes, we force a 'get'. Nothing can be done if we can't handle an event

  def applyCommand(state: State[A], cmd: Command): (Events, State[A]) = {
    val evts       = onCommand(state, cmd)
    val updatedAgg = onEvents(state, evts)
    (evts, updatedAgg)
  }
}

object IdentityInterpreter {
  def apply[A <: AggregateLike](behavior: Behavior[A], atMost: Duration = 5.seconds) = new IdentityInterpreter(behavior, atMost)
}
