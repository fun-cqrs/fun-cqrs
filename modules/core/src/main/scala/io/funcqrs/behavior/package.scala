package io.funcqrs

package object behavior {

  type Behavior[A <: AggregateLike] = PartialFunction[State[A], Actions[A]]

  type BehaviorUnwrapped[A <: AggregateLike] = PartialFunction[A, Actions[A]]

  /**
   * A CommandToInvoker is a PartialFunction from a DomainCommand to a CommandHandlerInvoker
   */
  type CommandToInvoker[C <: DomainCommand, E <: DomainEvent] = PartialFunction[C, CommandHandlerInvoker[C, E]]

  /**
   * An EventHandler is a PartialFunction from a DomainEvent to an Aggregate
   *
   * Typically it is used to construct or update an aggregate. In case of update, an Aggregate instance must be in scope.
   */
  type EventHandler[E <: DomainEvent, A <: AggregateLike] = PartialFunction[E, A]

  def actions[A <: AggregateLike] = Actions[A]()

  def action[A <: AggregateLike] = Actions[A]()

  def Behavior[A <: AggregateLike](onCreation: => Actions[A])(postCreation: BehaviorUnwrapped[A]): Behavior[A] = {
    case Uninitialized(_) => onCreation
    case Initialized(aggregate) if postCreation.isDefinedAt(aggregate) => postCreation(aggregate)
  }
}
