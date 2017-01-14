package io.funcqrs.behavior

import io.funcqrs.interpreters.Identity
import io.funcqrs.{ MissingCommandHandlerException, MissingEventHandlerException }

import scala.collection.immutable
import scala.language.higherKinds
import scala.reflect.ClassTag
import scala.util.{ Failure, Try }

case class Actions[A, C, E] private (cmdInvokers: CommandToInvoker[C, E],
                                     rejectCmdInvokers: CommandToInvoker[C, E],
                                     evtHandlers: EventHandler[E, A]) {

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
      val cmdHandler: PartialFunction[Command, Try[Events]] = {
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

  /** Declares a `Command Handler` that produces one single [[Event]] */
  def handleCommand[CC <: Command: ClassTag, EE <: Event](cmdHandler: CC => Identity[EE]): TypedActions = {
    // wrap single event in immutable.Seq
    val handlerWithSeq: CC => Identity[immutable.Seq[EE]] = (cmd: CC) => immutable.Seq(cmdHandler(cmd))
    handleCommand[CC, EE, immutable.Seq](handlerWithSeq)
  }

  def handleCommand[CC <: Command: ClassTag, EE <: Event, F[_]](cmdHandler: CC => F[EE])(implicit ivk: InvokerDirective[F]): TypedActions =
    addInvoker(ivk.newInvoker(cmdHandler))

//  def handleCommand[C <: Command: ClassTag, E <: Event, F[_]](cmdHandler: C => F[immutable.Seq[E]])(
//      implicit ivk: InvokerSeqDirective[F]): TypedActions =
//    addInvoker(ivk.newInvoker(cmdHandler))
//
//  def handleCommand[C <: Command: ClassTag, E <: Event, F[_]](cmdHandler: C => F[List[E]])(
//      implicit ivk: InvokerListDirective[F]): TypedActions =
//    addInvoker(ivk.newInvoker(cmdHandler))
//

  /**
    * Declares an event handler
    *
    * @param eventHandler - the event handler function
    * @return an Actions for A
    */
  def handleEvent(eventHandler: EventHandler[E, A]): TypedActions = {
    this.copy(evtHandlers = evtHandlers orElse eventHandler)
  }

  /**
    * Declares a guard clause that reject commands that fulfill a given condition.
    *
    * A guard clause is a `Command Handler` as it handles a incoming command,
    * but instead of producing Event, it returns a [[Throwable]] to signalize an error condition.
    *
    * Guard clauses command handlers have precedence over handlers producing Events.
    *
    * @param cmdHandler - a PartialFunction from Command to [[Throwable]].
    * @return - return a [[Actions]].
    */
  def reject(cmdHandler: PartialFunction[C, Throwable]): Actions[A, C, E] = {

    val invokerPF: CommandToInvoker[C, E] = {
      case cmd if cmdHandler.isDefinedAt(cmd) =>
        TryCommandHandlerInvoker(cmd => Failure(cmdHandler(cmd)))
    }

    this.copy(
      rejectCmdInvokers = rejectCmdInvokers orElse invokerPF
    )
  }

  /** Alias for reject */
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

  private def addInvoker[CC <: Command: ClassTag, EE <: Event](invoker: CommandHandlerInvoker[CC, EE]): TypedActions = {

    // as such we can detect if a duplicated key is added
    object CmdExtractor extends ClassTagExtractor[CC]
    // PF from Cmd to Invoker
    val invokerPF: CommandToInvoker[CC, EE] = { case CmdExtractor(cmd) => invoker }
    // add it
    this.copy(
      cmdInvokers = cmdInvokers orElse invokerPF.asInstanceOf[CommandToInvoker[Command, Event]]
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
