package io.strongtyped.funcqrs

import scala.concurrent.{ExecutionContext, Future}

trait Behavior[A <: Aggregate] {

  type AggregateType = A
  type Command = A#Protocol#ProtocolCommand
  type Event = A#Protocol#ProtocolEvent
  type Events = Seq[Event]


  def executionContext: ExecutionContext = {
    scala.concurrent.ExecutionContext.global
  }

  def applyEvent(event: Event): AggregateType

  def applyEvent(event: Event, aggregate: AggregateType): AggregateType

  /**
   * Apply a list of events to an Aggregate
   * @return the updated Aggregate
   */
  final def applyEvents(events: Events, aggregate: AggregateType): AggregateType = {
    events.foldLeft(aggregate) { (aggregate, event) =>
      applyEvent(event, aggregate)
    }
  }

  def validate(cmd: Command): Future[Event] = {
    validateAsync(cmd)(executionContext)
  }

  def validate(cmd: Command, aggregate: AggregateType): Future[Events] = {
    validateAsync(cmd, aggregate)(executionContext)
  }

  def applyCommand(cmd: Command): Future[(Event, AggregateType)] = {
    applyAsyncCommand(cmd)(executionContext)
  }

  def applyCommand(cmd: Command, aggregate: AggregateType): Future[(Events, AggregateType)] = {
    applyAsyncCommand(cmd, aggregate)(executionContext)
  }


  // async behavior
  protected def validateAsync(cmd: Command)(implicit ec: ExecutionContext): Future[Event]

  protected def validateAsync(cmd: Command, aggregate: AggregateType)(implicit ec: ExecutionContext): Future[Events]


  protected def applyAsyncCommand(cmd: Command)(implicit ec: ExecutionContext): Future[(Event, AggregateType)] = {
    validateAsync(cmd).map { event =>
      (event, applyEvent(event))
    }
  }

  protected def applyAsyncCommand(cmd: Command, aggregate: AggregateType)(implicit ec: ExecutionContext): Future[(Events, AggregateType)] = {
    validateAsync(cmd, aggregate).map { events =>
      (events, applyEvents(events, aggregate))
    }
  }
}


object Behavior {
  def empty[A <: Aggregate]: Behavior[A] = new Behavior[A] {
    def applyEvent(event: Event): AggregateType = ???
    def applyEvent(event: Event, aggregate: AggregateType): AggregateType = ???
    // async behavior
    protected def validateAsync(cmd: Command)(implicit ec: ExecutionContext): Future[Event] =
      Future.failed(new CommandException(s"Empty Behavior, can't command $cmd"))

    protected def validateAsync(cmd: Command, aggregate: AggregateType)(implicit ec: ExecutionContext): Future[Events] =
      Future.failed(new CommandException(s"Empty Behavior, can't command $cmd"))

  }
}