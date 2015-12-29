package io.funcqrs.interpreters

import io.funcqrs.AggregateLike
import io.funcqrs.behavior.{ Behavior, FutureCommandHandlerInvoker, IdCommandHandlerInvoker, TryCommandHandlerInvoker }

import scala.concurrent.Await
import scala.concurrent.duration.{ Duration, _ }
import scala.util.Try

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

  def handleCommand(cmd: Command): Try[Events] = {

    behavior.handleCommand(cmd) match {
      case IdCommandHandlerInvoker(handler)     => Try(handler(cmd))
      case TryCommandHandlerInvoker(handler)    => handler(cmd)
      case FutureCommandHandlerInvoker(handler) => Try(Await.result(handler(cmd), atMost))
    }

  }

  def handleCommand(aggregate: A, cmd: Command): Try[Events] = {

    behavior.handleCommand(aggregate, cmd) match {
      case IdCommandHandlerInvoker(handler)     => Try(handler(cmd))
      case TryCommandHandlerInvoker(handler)    => handler(cmd)
      case FutureCommandHandlerInvoker(handler) => Try(Await.result(handler(cmd), atMost))
    }

  }
}

object TryInterpreter {
  def apply[A <: AggregateLike](behavior: Behavior[A], atMost: Duration = 5.seconds) = new TryInterpreter(behavior, atMost)
}
