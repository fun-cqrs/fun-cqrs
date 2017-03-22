package io.funcqrs

import io.funcqrs.behavior.handlers.CommandHandlerInvoker

import scala.collection.immutable
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

  class BehaviorBuilderAndThen[A, C, E](creationActions: => Actions[A, C, E]) {

    /**
      * Adds aggregate actions for post-construction phase.
      *
      * This method receives a [[PartialFunction]] from Aggregate to Actions.
      */
    def andThen(updateActions: AggregateToActions[A, C, E]): Behavior[A, C, E] = {
      case None                                                    => creationActions
      case Some(aggregate) if updateActions.isDefinedAt(aggregate) => updateActions(aggregate)
    }

  }

  object BehaviorBuilderFirst {

    /**
      * Adds aggregate actions for construction phase.
      *
      * In construction phase we must declare the Command(s) that will trigger the
      * first event that will fill the aggregate constructor.
      *
      * We call them the seed command and the seed event.
      */
    def first[A, C, E](creationActions: => Actions[A, C, E]) =
      new BehaviorBuilderAndThen(creationActions)

    @deprecated(message = "use create instead", since = "1.0.0-M1")
    def construct[A, C, E](creationActions: => Actions[A, C, E]) = first(creationActions)
  }

  def Behavior = BehaviorBuilderFirst
}
