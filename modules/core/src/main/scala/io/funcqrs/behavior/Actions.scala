package io.funcqrs.behavior

import io.funcqrs.behavior.handlers.{ CommandHandler, CommandHandlerInvoker, TryCommandHandlerInvoker }
import io.funcqrs.{ MissingCommandHandlerException, MissingEventHandlerException }

import scala.collection.immutable
import scala.language.higherKinds
import scala.reflect.ClassTag
import scala.util.{ Failure, Try }

case class Actions[A, C, E] private (private val cmdInvokers: CommandToInvoker[C, E],
                                     private val rejectCmdInvokers: CommandToInvoker[C, E],
                                     private val evtHandlers: EventHandler[E, A]) {

  type Aggregate = A
  type Command   = C
  type Event     = E
  type Events    = immutable.Seq[Event]

  type TypedActions = Actions[Aggregate, Command, Event]

  /**
    * All command handlers together.
    * First reject handlers, then normal command handlers
    */
  private val allHandlers = rejectCmdInvokers orElse cmdInvokers

  /**
    * Returns a [[CommandHandlerInvoker]] for the passed [[Command]]. Invokers are delayed execution
    * of `Command Handlers` and abstract over the Functor that will be returned when handling the command.
    *
    * Internally, this method calls the declared `Command Handlers`.
    *
    */
  def onCommand(cmd: Command): CommandHandlerInvoker[Command, Event] = {
    if (allHandlers.isDefinedAt(cmd))
      allHandlers(cmd)
    else {
      val cmdHandler: CommandToManyEvents[C, E, Try] = {
        case _ =>
          val msg = s"No command handlers defined for command: $cmd"
          Failure(new MissingCommandHandlerException(msg))
      }
      // return a fallback invoker if not define
      TryCommandHandlerInvoker(cmdHandler)
    }
  }

  /**
    * Applies the passed [[Event]] producing a new instance of [[Aggregate]].
    * Internally, this method calls the declared `Event Handlers`.
    *
    * @throws MissingEventHandlerException if no Event handler is defined for the passed event.
    */
  def onEvent(evt: Event): Aggregate = {
    if (evtHandlers.isDefinedAt(evt))
      evtHandlers(evt)
    else
      throw new MissingEventHandlerException(s"No event handlers defined for events: $evt")
  }

  def commandHandler[F[_], G[_]](cmdHandler: CommandHandler[C, E, F, G]): TypedActions = {
    addInvoker(cmdHandler.invoker)
  }

  private def addInvoker(invoker: CommandHandlerInvoker[C, E]): TypedActions = {

    val invokerPF: CommandToInvoker[C, E] = {
      case cmd if invoker.commandHandler.isDefinedAt(cmd) => invoker
    }

    this.copy(cmdInvokers = cmdInvokers orElse invokerPF)
  }

  /**
    * Declares an event handler
    *
    * @param handler - the event handler function
    * @return an Actions for A
    */
  def eventHandler(handler: EventHandler[E, A]): TypedActions = {
    this.copy(evtHandlers = evtHandlers orElse handler)
  }

  /**
    * Declares a guard clause that reject commands that fulfill a given condition.
    *
    * A guard clause is a `Command Handler` as it handles an incoming command,
    * but instead of producing Event, it returns a [[Throwable]] to signalize an error condition.
    *
    * Guard clauses command handlers have precedence over handlers producing Events.
    *
    * @param cmdHandler - a PartialFunction from Command to [[Throwable]].
    * @return - return a [[Actions]].
    */
  def reject(cmdHandler: PartialFunction[C, Throwable]): Actions[A, C, E] = {

    val cmdHandlerSeq: CommandToManyEvents[C, E, Try] = {
      case cmd => Failure(cmdHandler(cmd))
    }
    // return a fallback invoker if not define

    val invokerPF: CommandToInvoker[C, E] = {
      case cmd if cmdHandler.isDefinedAt(cmd) => TryCommandHandlerInvoker(cmdHandlerSeq)
    }

    this.copy(
      rejectCmdInvokers = rejectCmdInvokers orElse invokerPF
    )
  }

  /** Alias to reject */
  def rejectCommand(cmdHandler: PartialFunction[C, Throwable]): Actions[A, C, E] = reject(cmdHandler)

  /**
    * Concatenate `this` Actions with `that` Actions
    */
  def ++(that: Actions[A, C, E]) = {
    Actions[A, C, E](
      cmdInvokers       = this.cmdInvokers orElse that.cmdInvokers,
      rejectCmdInvokers = this.rejectCmdInvokers orElse that.rejectCmdInvokers,
      evtHandlers       = this.evtHandlers orElse that.evtHandlers
    )
  }

}

object Actions {

  def apply[A, C, E](): Actions[A, C, E] =
    new Actions[A, C, E](
      cmdInvokers       = PartialFunction.empty,
      rejectCmdInvokers = PartialFunction.empty,
      evtHandlers       = PartialFunction.empty
    )

  def empty[A, C, E]: Actions[A, C, E] = apply()
}
