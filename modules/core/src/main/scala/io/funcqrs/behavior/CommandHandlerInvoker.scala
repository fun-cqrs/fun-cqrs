package io.funcqrs.behavior

import io.funcqrs.interpreters._
import io.funcqrs.{ DomainCommand, DomainEvent }

import scala.collection.immutable
import scala.concurrent.Future
import scala.language.higherKinds
import scala.util.Try

/**
  * A CommandHandlerInvoker holds a PartialFunction from DomainCommand to F[immutable.Seq[DomainEvent]].
  * F being the higher-kind wrapping the result of handling the command.
  *
  * Invokers are delayed execution of `Command Handlers` and abstract over the
  * Functor that will be returned when handling the command. Fun.CQRS provides three CommandHandlerInvoker implementations:
  * IdCommandHandlerInvoker (returns the Identity Functor), TryCommandHandlerInvoker and FutureCommandHandlerInvoker.
  */
trait CommandHandlerInvoker[C, E] {

  type F[_]

  def commandHandler: CommandToManyEvents[C, E, F] // C => F[immutable.Seq[E]]

  def apply(cmd: C): F[immutable.Seq[E]] = commandHandler(cmd)
}

/** A CommandHandlerInvoker which F type member is defined as [[Identity]] */
case class IdCommandHandlerInvoker[C, E](commandHandler: CommandToManyEvents[C, E, Identity]) extends CommandHandlerInvoker[C, E] {

  type F[_] = Identity[_]
}

/** A CommandHandlerInvoker which F type member is defined as [[Try]] */
case class TryCommandHandlerInvoker[C, E](commandHandler: CommandToManyEvents[C, E, Try]) extends CommandHandlerInvoker[C, E] {

  type F[_] = Try[_]
}

/** A CommandHandlerInvoker which F type member is defined as [[Future]] */
case class FutureCommandHandlerInvoker[C, E](commandHandler: CommandToManyEvents[C, E, Future]) extends CommandHandlerInvoker[C, E] {

  type F[_] = Future[_]
}
