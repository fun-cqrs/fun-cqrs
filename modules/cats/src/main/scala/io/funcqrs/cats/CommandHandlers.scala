import cats.data.{NonEmptyList, Validated}
import io.funcqrs.behavior.CommandToManyEvents
import io.funcqrs.behavior.handlers.{CommandHandler, CommandHandlerInvoker}
import io.funcqrs.interpreters.Identity

import scala.collection.immutable
import scala.concurrent.Future

trait ValidationError

trait ValidationTypes {

  /** A [[NonEmptyList]] of [[ValidationError]] */
  type Errors = NonEmptyList[ValidationError]

  type Validation[+T] = Validated[Errors, T]
}

object validated extends ValidationTypes {

  case class ValidationCommandHandlerInvoker[C, E](commandHandler: CommandToManyEvents[C, E, Validation]) extends CommandHandlerInvoker[C, E] {

    type F[_] = Validation[_]
  }

  case class OneEvent[C, E](handler: PartialFunction[C, Validation[E]]) extends CommandHandler[C, E, Validation, Identity] {
    def invoker: CommandHandlerInvoker[C, E] = {

      val handlerWithSeq: CommandToManyEvents[C, E, Validation] = {
        case cmd if handler.isDefinedAt(cmd) => handler(cmd).map(immutable.Seq(_))
      }

      ValidationCommandHandlerInvoker(handlerWithSeq)
    }
  }

  case class ManyEvents[C, E](handler: PartialFunction[C, Validation[immutable.Seq[E]]]) extends CommandHandler[C, E, Validation, immutable.Seq] {
    def invoker: CommandHandlerInvoker[C, E] = ValidationCommandHandlerInvoker(handler)
  }

}

/** A CommandHandlerInvoker which F type member is defined as [[Future]] */
