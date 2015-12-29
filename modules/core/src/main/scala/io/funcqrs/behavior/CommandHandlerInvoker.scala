package io.funcqrs.behavior

import io.funcqrs.interpreters._
import io.funcqrs.{ DomainCommand, DomainEvent }

import scala.collection.immutable
import scala.concurrent.Future
import scala.language.higherKinds
import scala.util.Try

/** A CommandHandlerInvoker holds a PartialFunction from DomainCommand to F[immutable.Seq[DomainEvent]].
  * F being the higher-kind wrapping the result of handling the command.
  */
trait CommandHandlerInvoker[-C <: DomainCommand, E <: DomainEvent] {

  type F[_]

  def cmdHandler: PartialFunction[C, F[immutable.Seq[E]]]
}


/** A CommandHandlerInvoker which F type member is defined as [[Identity]] */
case class IdCommandHandlerInvoker[C <: DomainCommand, E <: DomainEvent](cmdHandler: PartialFunction[C, Identity[immutable.Seq[E]]])
    extends CommandHandlerInvoker[C, E] {

  type F[_] = Identity[_]
}


/** A CommandHandlerInvoker which F type member is defined as [[Try]] */
case class TryCommandHandlerInvoker[C <: DomainCommand, E <: DomainEvent](cmdHandler: PartialFunction[C, Try[immutable.Seq[E]]])
    extends CommandHandlerInvoker[C, E] {

  type F[_] = Try[_]
}


/** A CommandHandlerInvoker which F type member is defined as [[Future]] */
case class FutureCommandHandlerInvoker[C <: DomainCommand, E <: DomainEvent](cmdHandler: PartialFunction[C, Future[immutable.Seq[E]]])
    extends CommandHandlerInvoker[C, E] {

  type F[_] = Future[_]
}

