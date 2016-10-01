package io.funcqrs.interpreters

import io.funcqrs.AggregateLike
import io.funcqrs.behavior._

import scala.concurrent.{ Future, Await }
import scala.concurrent.duration.{ Duration, _ }
import scala.util.{ Failure, Success, Try }

/**
 * An Interpreter with F[_] bounded to [[Try]].
 *
 * All command handling are interpreted to [[Try]] of Events.
 *
 * Will block on any async operation defined by [[Behavior]].
 *
 * This interpreter should be used for testing and / or for behaviors that preferably don't define any async operation.
 *
 * @param behavior - a Aggregate [[Behavior]]
 * @param atMost - the maximum duration we are to wait before Futures timeout.
 * @tparam A - an Aggregate type
 */
class TryInterpreter[A <: AggregateLike](val behavior: Behavior[A], atMost: Duration = 5.seconds) extends Interpreter[A, Try] {

  protected def interpret: InterpreterFunction = {
    case (cmd, IdCommandHandlerInvoker(handler)) => Try(handler(cmd))
    case (cmd, TryCommandHandlerInvoker(handler)) => handler(cmd)
    case (cmd, FutureCommandHandlerInvoker(handler)) => Try(Await.result(handler(cmd), atMost))
  }

  protected def fromTry[B](any: Try[B]): Try[B] = any

  def applyCommand(state: State[A], cmd: Command): Try[(Events, State[A])] = {
    for {
      evts <- onCommand(state, cmd)
      updatedAgg <- onEvents(state, evts)
    } yield (evts, updatedAgg)
  }
}

object TryInterpreter {
  def apply[A <: AggregateLike](behavior: Behavior[A], atMost: Duration = 5.seconds) = new TryInterpreter(behavior, atMost)
}
