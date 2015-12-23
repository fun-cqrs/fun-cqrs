package io.funcqrs.behavior

import io.funcqrs.interpreters._
import io.funcqrs.{ DomainCommand, DomainEvent }

import scala.collection.immutable
import scala.concurrent.Future
import scala.language.higherKinds
import scala.util.Try

trait CommandHandlerInvoker[-C <: DomainCommand, E <: DomainEvent] {

  type F[_]

  def cmdHandler: PartialFunction[C, F[immutable.Seq[E]]]
}

case class IdCommandHandlerInvoker[C <: DomainCommand, E <: DomainEvent](cmdHandler: PartialFunction[C, Identity[immutable.Seq[E]]])
    extends CommandHandlerInvoker[C, E] {

  type F[_] = Identity[_]
}

case class TryCommandHandlerInvoker[C <: DomainCommand, E <: DomainEvent](cmdHandler: PartialFunction[C, Try[immutable.Seq[E]]])
    extends CommandHandlerInvoker[C, E] {

  type F[_] = Try[_]
}

case class FutureCommandHandlerInvoker[C <: DomainCommand, E <: DomainEvent](cmdHandler: PartialFunction[C, Future[immutable.Seq[E]]])
    extends CommandHandlerInvoker[C, E] {

  type F[_] = Future[_]
}

