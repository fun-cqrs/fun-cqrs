package io.funcqrs.interpreters

import io.funcqrs.AggregateLike
import io.funcqrs.behavior._

import scala.concurrent.Future
import scala.language.higherKinds
import scala.util.Try

/**
 * An Interpreter with F[_] bounded to [[Future]].
 *
 * All command handling are interpreted to [[Future]] of Events.
 *
 * @param behavior - a Aggregate [[Behavior]]
 * @tparam A - an Aggregate type
 */
class AsyncInterpreter[A <: AggregateLike](val behavior: Behavior[A]) extends Interpreter[A, Future] {

  protected def interpret: InterpreterFunction = {
    case (cmd, IdCommandHandlerInvoker(handler)) => Future.successful(handler(cmd))
    case (cmd, TryCommandHandlerInvoker(handler)) => Future.fromTry(handler(cmd))
    case (cmd, FutureCommandHandlerInvoker(handler)) => handler(cmd)
  }

  protected def onCommandFailure(ex: Throwable): Future[Events] = Future.failed(ex)
}

object AsyncInterpreter {
  def apply[A <: AggregateLike](behavior: Behavior[A]) = new AsyncInterpreter(behavior)
}