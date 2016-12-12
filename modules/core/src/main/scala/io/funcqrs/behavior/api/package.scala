package io.funcqrs.behavior

package object api {

  type Behavior[A, C, E] = PartialFunction[Option[A], Actions[A, C, E]]

  type AggregateToActions[A, C, E] = PartialFunction[A, Actions[A, C, E]]

  /**
    * A CommandToInvoker is a PartialFunction from a Command to a CommandHandlerInvoker
    */
  type CommandToInvoker[C, E] = PartialFunction[C, CommandHandlerInvoker[C, E]]

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
