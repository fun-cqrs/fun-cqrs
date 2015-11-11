package io.strongtyped.funcqrs

import scala.concurrent.{ ExecutionContext, Future }
import scala.collection.immutable

trait Behavior[Aggregate <: AggregateDef] extends AggregateTypes[Aggregate] {

  /** The ExecutionContext to be used when calling validateAsync methods. Defaults to [[scala.concurrent.ExecutionContext.global]]
    *
    * Override this method if you prefer to use another ExecutionContext.
    *
    * Note: this is done on purpose to avoid using the EC from Akka for instance.
    * It's recommended to avoid using Akka's context.dispatcher to run client code.
    *
    * @return - ExecutionContext to be used when calling validateAsync methods.
    */
  def executionContext: ExecutionContext = {
    scala.concurrent.ExecutionContext.global
  }

  def applyEvent(event: Event): Aggregate

  def applyEvent(event: Event, aggregate: Aggregate): Aggregate

  /** Apply a list of events to an Aggregate
    * @return the updated Aggregate
    */
  final def applyEvents(events: Events, aggregate: Aggregate): Aggregate = {
    events.foldLeft(aggregate) { (aggregate, event) =>
      applyEvent(event, aggregate)
    }
  }

  def validate(cmd: Command): Future[Event] = {
    validateAsync(cmd)(executionContext)
  }

  def validate(cmd: Command, aggregate: Aggregate): Future[Events] = {
    validateAsync(cmd, aggregate)(executionContext)
  }

  def applyCommand(cmd: Command): Future[(Event, Aggregate)] = {
    applyAsyncCommand(cmd)(executionContext)
  }

  def applyCommand(cmd: Command, aggregate: Aggregate): Future[(Events, Aggregate)] = {
    applyAsyncCommand(cmd, aggregate)(executionContext)
  }

  // async behavior
  protected def validateAsync(cmd: Command)(implicit ec: ExecutionContext): Future[Event]

  protected def validateAsync(cmd: Command, aggregate: Aggregate)(implicit ec: ExecutionContext): Future[Events]

  protected def applyAsyncCommand(cmd: Command)(implicit ec: ExecutionContext): Future[(Event, Aggregate)] = {
    validateAsync(cmd).map { event =>
      (event, applyEvent(event))
    }
  }

  protected def applyAsyncCommand(cmd: Command, aggregate: Aggregate)(implicit ec: ExecutionContext): Future[(Events, Aggregate)] = {
    validateAsync(cmd, aggregate).map { events =>
      (events, applyEvents(events, aggregate))
    }
  }
}

object Behavior {
  def empty[Aggregate <: AggregateDef]: Behavior[Aggregate] = new Behavior[Aggregate] {
    def applyEvent(event: Event): Aggregate = ???
    def applyEvent(event: Event, aggregate: Aggregate): Aggregate = ???
    // async behavior
    protected def validateAsync(cmd: Command)(implicit ec: ExecutionContext): Future[Event] =
      Future.failed(new CommandException(s"Empty Behavior, can't accept command $cmd"))

    protected def validateAsync(cmd: Command, aggregate: Aggregate)(implicit ec: ExecutionContext): Future[Events] =
      Future.failed(new CommandException(s"Empty Behavior, can't accept command $cmd"))

  }
}