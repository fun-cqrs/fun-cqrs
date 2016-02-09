package io.funcqrs.behavior

import io.funcqrs._

import scala.util.{ Failure, Try }

trait Behavior[A <: AggregateLike] extends AggregateAliases {

  type Aggregate = A

  def commandHandler: PartialFunction[(Option[Aggregate], Command), CommandHandlerInvoker[Command, Event]]
  def eventListener: PartialFunction[(Option[Aggregate], Event), Aggregate]

  def handleCommand(optionalAggregate: Option[Aggregate], cmd: Command): CommandHandlerInvoker[Command, Event] = {
    if (commandHandler.isDefinedAt((optionalAggregate, cmd)))
      commandHandler((optionalAggregate, cmd))
    else
      // return fallback invoker if not define
      fallbackInvoker(s"Invalid command $cmd for optional aggregate ${optionalAggregate.map(_.id)}")
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

  def onEvent(optionalAggregate: Option[Aggregate], evt: Event): Aggregate = {
    eventListener((optionalAggregate, evt))
  }

  def canHandleEvent(event: Event, optionalAggregate: Option[Aggregate]): Boolean = {
    eventListener.isDefinedAt((optionalAggregate, event))
  }

  def canHandleEvents(events: Events, optionalAggregate: Option[Aggregate]): Boolean = {
    events.forall { evt => canHandleEvent(evt, optionalAggregate) }
  }

}
