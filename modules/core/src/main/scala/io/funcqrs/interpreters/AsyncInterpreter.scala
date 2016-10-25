package io.funcqrs.interpreters

import io.funcqrs.AggregateLike
import io.funcqrs.behavior._

import scala.concurrent.Future
import scala.language.higherKinds
import scala.util.Try
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

  protected def interpret: InterpreterFunction = {
    case (cmd, IdCommandHandlerInvoker(handler))     => Future.successful(handler(cmd))
    case (cmd, TryCommandHandlerInvoker(handler))    => Future.fromTry(handler(cmd))
    case (cmd, FutureCommandHandlerInvoker(handler)) => handler(cmd)
  }

  protected def fromTry[B](any: Try[B]): Future[B] = Future.fromTry(any)

  def applyCommand(state: State[A], cmd: Command): Future[(Events, State[A])] =
    for {
      evts <- onCommand(state, cmd)
      updatedAgg <- onEvents(state, evts)
    } yield (evts, updatedAgg)
}

object AsyncInterpreter {
  def apply[A <: AggregateLike](behavior: Behavior[A]) = new AsyncInterpreter(behavior)
}
