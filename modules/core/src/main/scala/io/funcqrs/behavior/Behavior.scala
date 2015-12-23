package io.funcqrs.behavior

import io.funcqrs._

import scala.util.{ Failure, Try }

trait Behavior[A <: AggregateLike] extends AggregateAliases {

  type Aggregate = A

  def commandHandlerWhenCreating: PartialFunction[Command, CommandHandlerInvoker[Command, Event]]
  def eventListenerWhenCreating: PartialFunction[Event, Aggregate]
  def commandHandlerWhenUpdating: PartialFunction[(Aggregate, Command), CommandHandlerInvoker[Command, Event]]
  def eventListenerWhenUpdating: PartialFunction[(Aggregate, Event), Aggregate]

  def handleCommand(cmd: Command): CommandHandlerInvoker[Command, Event] = {

    if (commandHandlerWhenCreating.isDefinedAt(cmd))
      commandHandlerWhenCreating(cmd)
    else
      // return fallback invoker if not defined
      fallbackInvoker(s"Invalid command $cmd")
  }

  def handleCommand(aggregate: Aggregate, cmd: Command): CommandHandlerInvoker[Command, Event] = {

    if (commandHandlerWhenUpdating.isDefinedAt(aggregate, cmd))
      commandHandlerWhenUpdating(aggregate, cmd)
    else
      // return fallback invoker if not defined
      fallbackInvoker(s"Invalid command $cmd for aggregate ${aggregate.id}")

  }

  /** Build a TryCommandHandlerInvoker that will always return an Failure
    * Used internally to handle unknown commands
    */
  protected def fallbackInvoker(msg: String): CommandHandlerInvoker[Command, Event] = {
    val cmdHandler: PartialFunction[Command, Try[Events]] = {
      case cmd => Failure(new CommandException(msg))
    }
    TryCommandHandlerInvoker(cmdHandler)
  }

  def onEvent(evt: Event): A = {
    eventListenerWhenCreating(evt)
  }

  def onEvent(aggregate: A, evt: Event): A = {
    eventListenerWhenUpdating(aggregate, evt)
  }

  def canHandleEvent(event: Event): Boolean = {
    eventListenerWhenCreating.isDefinedAt(event)
  }

  def canHandleEvents(events: Events): Boolean = {
    events forall canHandleEvent
  }

  def canHandleEvent(event: Event, aggregate: Aggregate): Boolean = {
    eventListenerWhenUpdating.isDefinedAt(aggregate, event)
  }

  def canHandleEvents(events: Events, aggregate: Aggregate): Boolean = {
    events.forall { evt => canHandleEvent(evt, aggregate) }
  }

}
