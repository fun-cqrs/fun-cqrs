package io.funcqrs.behavior

import io.funcqrs.interpreters._
import io.funcqrs.{ CommandException, AggregateLike, AggregateAliases }

import scala.collection.immutable
import scala.concurrent.Future
import scala.language.higherKinds
import scala.reflect.ClassTag
import scala.util.{ Failure, Try }

case class Actions[A <: AggregateLike](
    cmdHandlerInvokers: CommandHandlerToInvoker[A#Command, A#Event] = PartialFunction.empty,
    rejectCmdInvokers: CommandHandlerToInvoker[A#Command, A#Event] = PartialFunction.empty,
    eventHandlers: EventHandler[A#Event, A] = PartialFunction.empty
) extends AggregateAliases {

  type Aggregate = A

  /**
   * All command handlers together.
   * First reject handlers, then normal command handlers
   */
  private val allHandlers = rejectCmdInvokers orElse cmdHandlerInvokers

  def onCommand(cmd: Command): CommandHandlerInvoker[Command, Event] = {
    if (allHandlers.isDefinedAt(cmd))
      allHandlers(cmd)
    else
      // return a fallback invoker if not define
      fallbackInvoker(s"Invalid command $cmd")
  }

  def onEvent(evt: Event): Aggregate = {
    eventHandlers(evt)
  }

  def canHandleEvent(event: Event): Boolean = {
    eventHandlers.isDefinedAt(event)
  }

  def canHandleEvents(events: Events): Boolean = {
    events.forall { evt => canHandleEvent(evt) }
  }

  /**
   * Build a TryCommandHandlerInvoker that will always return an Failure
   * Used internally to handle unknown commands
   */
  protected def fallbackInvoker(msg: String): CommandHandlerInvoker[Command, Event] = {
    val cmdHandler: PartialFunction[Command, Try[Events]] = {
      case cmd => Failure(new CommandException(msg))
    }
    TryCommandHandlerInvoker(cmdHandler)
  }

  def ++(that: Actions[A]) = {
    this.copy(
      cmdHandlerInvokers = this.cmdHandlerInvokers orElse that.cmdHandlerInvokers,
      rejectCmdInvokers = this.rejectCmdInvokers orElse that.rejectCmdInvokers,
      eventHandlers = this.eventHandlers orElse that.eventHandlers
    )
  }

  /**
   * Declares a guard clause that reject commands that fulfill a given condition
   * @param cmdHandler - a PartialFunction from Command to Throwable.
   * @return - return a [[Actions]].
   */
  def reject(cmdHandler: PartialFunction[A#Command, Throwable]): Actions[Aggregate] = {

    val invokerPF: CommandHandlerToInvoker[A#Command, A#Event] = {
      case cmd if cmdHandler.isDefinedAt(cmd) =>
        TryCommandHandlerInvoker(cmd => Failure(cmdHandler(cmd)))
    }
    this.copy(
      rejectCmdInvokers = rejectCmdInvokers orElse invokerPF
    )
  }

  def rejectCommand(cmdHandler: PartialFunction[Command, Throwable]): Actions[Aggregate] = reject(cmdHandler)

  def handleCommand[C <: Command: ClassTag, E <: Event](cmdHandler: C => Identity[E]): Actions[Aggregate] = {
    // wrap single event in immutable.Seq
    val handlerWithSeq: C => Identity[immutable.Seq[E]] = (cmd: C) => immutable.Seq(cmdHandler(cmd))
    handleCommand.manyEvents(handlerWithSeq)
  }

  def handleCommand: ManyEventsBinder[Identity] = IdentityManyEventsBinder(this)

  case class IdentityManyEventsBinder(behavior: Actions[A]) extends ManyEventsBinder[Identity] {

    def manyEvents[C <: Command: ClassTag, E <: Event](cmdHandler: (C) => Identity[immutable.Seq[E]]): Actions[Aggregate] = {

      object CmdExtractor extends ClassTagExtractor[C]

      val invokerPF: CommandHandlerToInvoker[C, E] = {
        case CmdExtractor(cmd) => IdCommandHandlerInvoker(cmdHandler)
      }

      behavior.copy(
        cmdHandlerInvokers = cmdHandlerInvokers orElse invokerPF.asInstanceOf[CommandHandlerToInvoker[Command, Event]]
      )
    }
  }

  def tryToHandleCommand[C <: Command: ClassTag, E <: Event](cmdHandler: C => Try[E]): Actions[Aggregate] = {
    // wrap single event in immutable.Seq
    val handlerWithSeq: (C) => Try[immutable.Seq[E]] = (cmd: C) => cmdHandler(cmd).map(immutable.Seq(_))
    tryToHandleCommand.manyEvents(handlerWithSeq)
  }

  def tryToHandleCommand: ManyEventsBinder[Try] = TryManyEventsBinder(this)

  case class TryManyEventsBinder(behavior: Actions[A]) extends ManyEventsBinder[Try] {
    def manyEvents[C <: Command: ClassTag, E <: Event](cmdHandler: (C) => Try[immutable.Seq[E]]): Actions[A] = {

      object CmdExtractor extends ClassTagExtractor[C]

      val invokerPF: CommandHandlerToInvoker[C, E] = {
        case CmdExtractor(cmd) => TryCommandHandlerInvoker(cmdHandler)
      }

      //consInvoker: PartialFunction[C, F[immutable.Seq[E]]] => CommandHandlerInvoker[C, E]
      behavior.copy(
        cmdHandlerInvokers = cmdHandlerInvokers orElse invokerPF.asInstanceOf[CommandHandlerToInvoker[Command, Event]]
      )
    }
  }

  def handleCommandAsync[C <: Command: ClassTag, E <: Event](cmdHandler: C => Future[E]): Actions[Aggregate] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    // wrap single event in immutable.Seq
    val handlerWithSeq: (C) => Future[immutable.Seq[E]] = (cmd: C) => cmdHandler(cmd).map(immutable.Seq(_))
    handleCommandAsync.manyEvents(handlerWithSeq)
  }

  def handleCommandAsync: ManyEventsBinder[Future] = FutureManyEventsBinder(this)

  case class FutureManyEventsBinder(behavior: Actions[A]) extends ManyEventsBinder[Future] {

    def manyEvents[C <: Command: ClassTag, E <: Event](cmdHandler: (C) => Future[immutable.Seq[E]]): Actions[A] = {

      object CmdExtractor extends ClassTagExtractor[C]

      val invokerPF: CommandHandlerToInvoker[C, E] = {
        case CmdExtractor(cmd) => FutureCommandHandlerInvoker(cmdHandler)
      }

      //consInvoker: PartialFunction[C, F[immutable.Seq[E]]] => CommandHandlerInvoker[C, E]
      behavior.copy(
        cmdHandlerInvokers = cmdHandlerInvokers orElse invokerPF.asInstanceOf[CommandHandlerToInvoker[Command, Event]]
      )
    }
  }

  /**
   * Declares an event handler
   *
   * @param eventHandler - the event handler function
   * @return an Actions for A
   */

  def handleEvent[E <: Event: ClassTag](eventHandler: E => A): Actions[Aggregate] = {

    object EvtExtractor extends ClassTagExtractor[E]

    val eventHandlerPF: EventHandler[A#Event, A] = {
      case EvtExtractor(evt) => eventHandler(evt)
    }
    this.copy(eventHandlers = eventHandlers orElse eventHandlerPF)
  }

  trait ManyEventsBinder[F[_]] {
    def manyEvents[C <: Command: ClassTag, E <: Event](cmdHandler: C => F[immutable.Seq[E]]): Actions[Aggregate]
  }

  @deprecated(message = "Use handleEvent instead", since = "0.3.1")
  def listener[E <: Event: ClassTag](eventHandler: E => A): Actions[Aggregate] =
    handleEvent(eventHandler)

  @deprecated(message = "Use handleCommand instead", since = "0.3.1")
  def handler[C <: Command: ClassTag, E <: Event](cmdHandler: C => Identity[E]): Actions[Aggregate] =
    handleCommand(cmdHandler)

  @deprecated(message = "Use handleCommand instead", since = "0.3.1")
  def handler: ManyEventsBinder[Identity] = handleCommand

  @deprecated(message = "Use tryToHandleCommand instead", since = "0.3.1")
  def tryHandler[C <: Command: ClassTag, E <: Event](cmdHandler: C => Try[E]): Actions[Aggregate] =
    tryToHandleCommand(cmdHandler)

  @deprecated(message = "Use tryToHandleCommand instead", since = "0.3.1")
  def tryHandler: ManyEventsBinder[Try] = tryToHandleCommand

  @deprecated(message = "Use handleCommandAsync instead", since = "0.3.1")
  def asyncHandler[C <: Command: ClassTag, E <: Event](cmdHandler: C => Future[E]): Actions[Aggregate] =
    handleCommandAsync(cmdHandler)

  @deprecated(message = "Use handleCommandAsync instead", since = "0.3.1")
  def asyncHandler: ManyEventsBinder[Future] = handleCommandAsync
}

object Actions {

  def apply[A <: AggregateLike]: Actions[A] = Actions[A]()

}