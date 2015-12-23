package io.funcqrs.interpreters

import io.funcqrs.AggregateLike
import io.funcqrs.behavior.{ Behavior, FutureCommandHandlerInvoker, IdCommandHandlerInvoker, TryCommandHandlerInvoker }

import scala.concurrent.Await
import scala.concurrent.duration.{ Duration, _ }

class IdentityInterpreter[A <: AggregateLike](val behavior: Behavior[A], atMost: Duration = 5.seconds) extends Interpreter[A, Identity] {

  def handleCommand(cmd: Command): Identity[Events] = {

    behavior.handleCommand(cmd) match {
      case IdCommandHandlerInvoker(handler)     => handler(cmd)
      case TryCommandHandlerInvoker(handler)    => handler(cmd).get
      case FutureCommandHandlerInvoker(handler) => Await.result(handler(cmd), atMost)
    }

  }

  def handleCommand(aggregate: A, cmd: Command): Identity[Events] = {

    behavior.handleCommand(aggregate, cmd) match {
      case IdCommandHandlerInvoker(handler)     => handler(cmd)
      case TryCommandHandlerInvoker(handler)    => handler(cmd).get
      case FutureCommandHandlerInvoker(handler) => Await.result(handler(cmd), atMost)
    }

  }

}

object IdentityInterpreter {
  def apply[A <: AggregateLike](behavior: Behavior[A], atMost: Duration = 5.seconds) = new IdentityInterpreter(behavior, atMost)
}