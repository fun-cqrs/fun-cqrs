package io.funcqrs.cats

import cats.Functor
import cats.data.Validated.Invalid
import cats.data.{NonEmptyList, ValidatedNel}
import io.funcqrs.behavior.CommandToManyEvents
import io.funcqrs.behavior.handlers.{CommandHandler, CommandHandlerInvoker}
import io.funcqrs.interpreters.Identity

import scala.collection.immutable
import scala.language.higherKinds

/**
  * Users of `validated` should implement hierarchy of errors that has root of `ValidationError`.
  */
trait ValidationError

/**
  * @tparam VE - validation error type
  * @tparam F2 - functor type
  */
trait ValidatedCommandHandlers[VE, F2[_]] {

  type Validation[T] = ValidatedNel[VE, F2[T]]

  def singleError[Err](e: Err): Invalid[NonEmptyList[Err]] = Invalid(NonEmptyList.of(e))

  case class ValidatedCommandHandlerInvoker[C, E](commandHandler: CommandToManyEvents[C, E, Validation]) extends CommandHandlerInvoker[C, E] {

    type F[_] = Validation[_]
  }

  //  val functor = implicitly[Functor[F2]]

  case class OneEvent[C, E](handler: PartialFunction[C, Validation[E]])
    (implicit functor: Functor[F2]) extends CommandHandler[C, E, Validation, Identity] {
    def invoker: CommandHandlerInvoker[C, E] = {

      val handlerWithSeq: CommandToManyEvents[C, E, Validation] = {
        case cmd if handler.isDefinedAt(cmd) => handler(cmd).map(fa => functor.map(fa)(immutable.Seq(_)))
      }

      ValidatedCommandHandlerInvoker(handlerWithSeq)
    }
  }

  case class ManyEvents[C, E](handler: PartialFunction[C, Validation[immutable.Seq[E]]]) extends CommandHandler[C, E, Validation, immutable.Seq] {
    def invoker: CommandHandlerInvoker[C, E] = ValidatedCommandHandlerInvoker(handler)
  }

}

object validatedId extends ValidatedCommandHandlers[ValidationError, cats.Id]