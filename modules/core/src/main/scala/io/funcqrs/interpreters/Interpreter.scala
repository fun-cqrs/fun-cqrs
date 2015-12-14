package io.funcqrs.interpreters

import io.funcqrs.dsl.AggregateSpec
import io.funcqrs.dsl.BindingDsl.Api.{ FutureCommandHandlerInvoker, IdCommandHandlerInvoker, TryCommandHandlerInvoker }
import io.funcqrs.{ AggregateAliases, AggregateLike }

import scala.concurrent.duration.{ Duration, _ }
import scala.concurrent.{ Await, Future }
import scala.language.higherKinds
import scala.util.Try

trait Interpreter[A <: AggregateLike, F[_]] extends AggregateAliases {
  type Aggregate = A

  def spec: AggregateSpec[A]

  def handleCommand(cmd: Command): F[Events]

  def handleCommand(aggregate: Aggregate, cmd: Command): F[Events]

  def onEvent(evt: Event): A = {
    spec.creationSpec.eventListenerPF(evt)
  }

  def onEvent(aggregate: A, evt: Event): A = {
    spec.updateSpec.eventListenerPF(aggregate, evt)
  }
}

