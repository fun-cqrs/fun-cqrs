package io.funcqrs.interpreters
import io.funcqrs.AggregateLike
import io.funcqrs.dsl.AggregateSpec
import io.funcqrs.dsl.BindingDsl.Api.{ FutureCommandHandlerInvoker, IdCommandHandlerInvoker, TryCommandHandlerInvoker }

import scala.concurrent.Future
import scala.concurrent.duration.{ Duration, _ }
import scala.language.higherKinds

class AsyncInterpreter[A <: AggregateLike](val spec: AggregateSpec[A], atMost: Duration = 5.seconds) extends Interpreter[A, Future] {

  def handleCommand(cmd: Command): Future[Events] = {
    val invoker = spec.creationSpec.cmdHandlerInvokerPF(cmd)
    invoker match {
      case IdCommandHandlerInvoker(handler)     => Future.successful(handler(cmd))
      case TryCommandHandlerInvoker(handler)    => Future.fromTry(handler(cmd))
      case FutureCommandHandlerInvoker(handler) => handler(cmd)
    }
  }

  def handleCommand(aggregate: A, cmd: Command): Future[Events] = {
    val invoker = spec.updateSpec.cmdHandlerInvokerPF(aggregate, cmd)
    invoker match {
      case IdCommandHandlerInvoker(handler)     => Future.successful(handler(cmd))
      case TryCommandHandlerInvoker(handler)    => Future.fromTry(handler(cmd))
      case FutureCommandHandlerInvoker(handler) => handler(cmd)
    }
  }
}
