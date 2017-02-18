package io.funcqrs

import scala.collection.immutable
import io.funcqrs.behavior.Actions

import scala.language.higherKinds

package object behavior {

  type Behavior[A, C, E] = PartialFunction[Option[A], Actions[A, C, E]]

  type AggregateToActions[A, C, E] = PartialFunction[A, Actions[A, C, E]]

  /**
    * A CommandToInvoker is a PartialFunction from a Command to a CommandHandlerInvoker
    */
  type CommandToInvoker[C, E] = PartialFunction[C, CommandHandlerInvoker[C, E]]

  type CommandToOneEvent[C, E, F[_]] = PartialFunction[C, F[E]]

  type CommandToManyEvents[C, E, F[_]] = PartialFunction[C, F[immutable.Seq[E]]]

  /**
    * An EventHandler is a PartialFunction from an Event to an aggregate
    *
    * Typically it is used to construct or update an aggregate. In case of update, an Aggregate instance must be in scope.
    */
  type EventHandler[E, A] = PartialFunction[E, A]

  class BehaviorStage[A, C, E](onCreation: => Actions[A, C, E]) {

    def andThen(postCreation: AggregateToActions[A, C, E]): Behavior[A, C, E] = {
      case None                                                   => onCreation
      case Some(aggregate) if postCreation.isDefinedAt(aggregate) => postCreation(aggregate)
    }
  }

  object BehaviorBuilder {
    def construct[A, C, E](onCreation: => Actions[A, C, E]) =
      new BehaviorStage(onCreation)
  }

  def Behavior = BehaviorBuilder
}
