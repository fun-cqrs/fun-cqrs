package io.funcqrs.interpreters

import io.funcqrs.behavior._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.higherKinds
import scala.util.Try

/**
  * An Interpreter with F[_] bounded to [[Future]].
  *
  * All command handling are interpreted to [[Future]] of Events.
  *
  * @param behavior - a Aggregate Behavior
  * @tparam A - the Aggregate type
  * @tparam C - the Command type
  * @tparam E - the Event type
  */
class AsyncInterpreter[A, C, E](val behavior: Behavior[A, C, E]) extends Interpreter[A, C, E, Future] {

  protected def interpret: InterpreterFunction = {
    case (cmd, IdCommandHandlerInvoker(handler))     => Future.successful(handler(cmd))
    case (cmd, TryCommandHandlerInvoker(handler))    => Future.fromTry(handler(cmd))
    case (cmd, FutureCommandHandlerInvoker(handler)) => handler(cmd)
  }

  protected def fromTry[B](any: Try[B]): Future[B] = Future.fromTry(any)

  def applyCommand(state: Option[A], cmd: Command): Future[(Events, Option[A])] =
    for {
      evts <- onCommand(state, cmd)
      updatedAgg <- onEvents(state, evts)
    } yield (evts, updatedAgg)
}

object AsyncInterpreter {
  def apply[A, C, E](behavior: Behavior[A, C, E]) = new AsyncInterpreter(behavior)
}
