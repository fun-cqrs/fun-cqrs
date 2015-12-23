package io.funcqrs.interpreters
import io.funcqrs.AggregateLike
import io.funcqrs.behavior.{ Behavior, FutureCommandHandlerInvoker, IdCommandHandlerInvoker, TryCommandHandlerInvoker }

import scala.concurrent.Future
import scala.language.higherKinds

class AsyncInterpreter[A <: AggregateLike](val behavior: Behavior[A]) extends Interpreter[A, Future] {

  def handleCommand(cmd: Command): Future[Events] = {

    behavior.handleCommand(cmd) match {
      case IdCommandHandlerInvoker(handler)     => Future.successful(handler(cmd))
      case TryCommandHandlerInvoker(handler)    => Future.fromTry(handler(cmd))
      case FutureCommandHandlerInvoker(handler) => handler(cmd)
    }
  }

  def handleCommand(aggregate: A, cmd: Command): Future[Events] = {

    behavior.handleCommand(aggregate, cmd) match {
      case IdCommandHandlerInvoker(handler)     => Future.successful(handler(cmd))
      case TryCommandHandlerInvoker(handler)    => Future.fromTry(handler(cmd))
      case FutureCommandHandlerInvoker(handler) => handler(cmd)
    }
  }
}

object AsyncInterpreter {
  def apply[A <: AggregateLike](behavior: Behavior[A]) = new AsyncInterpreter(behavior)
}