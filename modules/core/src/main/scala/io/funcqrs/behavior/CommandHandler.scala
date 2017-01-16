package io.funcqrs.behavior

import io.funcqrs.interpreters.Identity

import scala.collection.immutable
import scala.concurrent.Future
import scala.language.higherKinds
import scala.util.Try

trait CommandHandler[C, E, F[_], G[_]] {
  def handler: PartialFunction[C, F[G[E]]]
  def invoker: CommandHandlerInvoker[C, E]
}

case class OneEvent[C, E](handler: PartialFunction[C, E]) extends CommandHandler[C, E, Identity, Identity] {

  def invoker: CommandHandlerInvoker[C, E] = {

    val handlerWithSeq: CommandToManyEvents[C, E, Identity] = {
      case cmd if handler.isDefinedAt(cmd) => immutable.Seq(handler(cmd))
    }

    IdCommandHandlerInvoker(handlerWithSeq)
  }
}

case class ManyEvents[C, E](handler: PartialFunction[C, immutable.Seq[E]]) extends CommandHandler[C, E, Identity, immutable.Seq] {
  def invoker: CommandHandlerInvoker[C, E] = IdCommandHandlerInvoker(handler)
}

case class MaybeOneEvent[C, E](handler: PartialFunction[C, Option[E]]) extends CommandHandler[C, E, Identity, Option] {

  def invoker: CommandHandlerInvoker[C, E] = {

    val handlerWithSeq: CommandToManyEvents[C, E, Identity] = {
      case cmd if handler.isDefinedAt(cmd) => handler(cmd).map(immutable.Seq(_)).getOrElse(immutable.Seq())
    }

    IdCommandHandlerInvoker(handlerWithSeq)
  }
}

case class MaybeManyEvents[C, E](handler: PartialFunction[C, Option[immutable.Seq[E]]])
    extends CommandHandler[C, E, Option, immutable.Seq] {

  def invoker: CommandHandlerInvoker[C, E] = {

    val handlerWithSeq: CommandToManyEvents[C, E, Identity] = {
      case cmd if handler.isDefinedAt(cmd) => handler(cmd).getOrElse(immutable.Seq())
    }

    IdCommandHandlerInvoker(handlerWithSeq)
  }
}

case class EventuallyOneEvent[C, E](handler: PartialFunction[C, Future[E]]) extends CommandHandler[C, E, Future, Identity] {

  def invoker: CommandHandlerInvoker[C, E] = {

    import scala.concurrent.ExecutionContext.Implicits.global

    val handlerWithSeq: CommandToManyEvents[C, E, Future] = {
      case cmd if handler.isDefinedAt(cmd) => handler(cmd).map(immutable.Seq(_))
    }

    FutureCommandHandlerInvoker(handlerWithSeq)
  }
}

case class EventuallyManyEvents[C, E](handler: PartialFunction[C, Future[immutable.Seq[E]]])
    extends CommandHandler[C, E, Future, immutable.Seq] {
  def invoker: CommandHandlerInvoker[C, E] = FutureCommandHandlerInvoker(handler)
}

case class AttemptOneEvent[C, E](handler: PartialFunction[C, Try[E]]) extends CommandHandler[C, E, Try, Identity] {
  def invoker: CommandHandlerInvoker[C, E] = {

    val handlerWithSeq: CommandToManyEvents[C, E, Try] = {
      case cmd if handler.isDefinedAt(cmd) => handler(cmd).map(immutable.Seq(_))
    }

    TryCommandHandlerInvoker(handlerWithSeq)
  }
}
case class AttemptManyEvents[C, E](handler: PartialFunction[C, Try[immutable.Seq[E]]]) extends CommandHandler[C, E, Try, immutable.Seq] {
  def invoker: CommandHandlerInvoker[C, E] = TryCommandHandlerInvoker(handler)
}
