package io.funcqrs.interpreters

import io.funcqrs.AggregateLike
import io.funcqrs.dsl.AggregateSpec
import io.funcqrs.dsl.BindingDsl.Api.{ FutureCommandHandlerInvoker, TryCommandHandlerInvoker, IdCommandHandlerInvoker }
import scala.concurrent.duration._
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Try

class TryInterpreter[A <: AggregateLike](val spec: AggregateSpec[A], atMost: Duration = 5.seconds) extends Interpreter[A, Try] {

  def handleCommand(cmd: Command): Try[Events] = {

    val invoker = spec.creationSpec.cmdHandlerInvokerPF(cmd)

    invoker match {
      case IdCommandHandlerInvoker(handler)     => Try(handler(cmd))
      case TryCommandHandlerInvoker(handler)    => handler(cmd)
      case FutureCommandHandlerInvoker(handler) => Try(Await.result(handler(cmd), atMost))
    }

  }

  def handleCommand(aggregate: A, cmd: Command): Try[Events] = {
    val invoker = spec.updateSpec.cmdHandlerInvokerPF(aggregate, cmd)

    invoker match {
      case IdCommandHandlerInvoker(handler)     => Try(handler(cmd))
      case TryCommandHandlerInvoker(handler)    => handler(cmd)
      case FutureCommandHandlerInvoker(handler) => Try(Await.result(handler(cmd), atMost))
    }

  }
}
