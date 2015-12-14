package io.funcqrs.interpreters

import io.funcqrs.AggregateLike
import io.funcqrs.dsl.AggregateSpec
import io.funcqrs.dsl.BindingDsl.Api.{ FutureCommandHandlerInvoker, TryCommandHandlerInvoker, IdCommandHandlerInvoker }
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.duration._

class IdentityInterpreter[A <: AggregateLike](val spec: AggregateSpec[A], atMost: Duration = 5.seconds) extends Interpreter[A, Identity] {

  def handleCommand(cmd: Command): Identity[Events] = {

    val invoker = spec.creationSpec.cmdHandlerInvokerPF(cmd)

    invoker match {
      case IdCommandHandlerInvoker(handler)     => handler(cmd)
      case TryCommandHandlerInvoker(handler)    => handler(cmd).get
      case FutureCommandHandlerInvoker(handler) => Await.result(handler(cmd), atMost)
    }

  }

  def handleCommand(aggregate: A, cmd: Command): Identity[Events] = {
    val invoker = spec.updateSpec.cmdHandlerInvokerPF(aggregate, cmd)

    invoker match {
      case IdCommandHandlerInvoker(handler)     => handler(cmd)
      case TryCommandHandlerInvoker(handler)    => handler(cmd).get
      case FutureCommandHandlerInvoker(handler) => Await.result(handler(cmd), atMost)
    }

  }

}
