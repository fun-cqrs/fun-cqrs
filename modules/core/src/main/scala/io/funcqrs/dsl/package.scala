package io.funcqrs

import io.funcqrs.behavior.CommandHandlerInvoker
import io.funcqrs.interpreters._

import scala.collection.immutable
import scala.language.higherKinds
import scala.util.Try
import scala.concurrent.Future

package object dsl {

  /**
   * A CommandToEvents is a PartialFunction from a DomainCommand to a sequence of Events within an effect F[_]
   *
   * Out of the box, we provide factories to build CommandHandler where F[_] is: the [[Identity]], a [[Try]] or a [[Future]].
   */
  type CommandToEvents[C <: DomainCommand, E <: DomainEvent, F[_]] = PartialFunction[C, F[immutable.Seq[E]]]

  type CommandToInvoker[C <: DomainCommand, E <: DomainEvent] = PartialFunction[C, CommandHandlerInvoker[C, E]]
  /**
   * An EventToAggregate is a PartialFunction from a DomainEvent to an Aggregate
   *
   * Typically it is used to construct or update an aggregate. In case of update, an Aggregate instance must be in scope.
   */
  type EventToAggregate[E <: DomainEvent, A <: AggregateLike] = PartialFunction[E, A]

}
