package io.funcqrs

package object behavior {

  @deprecated
  type Behavior[A <: AggregateLike] = PartialFunction[State[A], ActionsDeprec[A]]
  @deprecated
  type BehaviorUnwrapped[A <: AggregateLike] = PartialFunction[A, ActionsDeprec[A]]

  /**
    * A CommandToInvoker is a PartialFunction from a DomainCommand to a CommandHandlerInvoker
    */
  @deprecated
  type CommandToInvoker[C <: DomainCommand, E <: DomainEvent] = PartialFunction[C, CommandHandlerInvoker[C, E]]

  /**
    * An EventHandler is a PartialFunction from a DomainEvent to an Aggregate
    *
    * Typically it is used to construct or update an aggregate. In case of update, an Aggregate instance must be in scope.
    */
  @deprecated
  type EventHandler[E <: DomainEvent, A <: AggregateLike] = PartialFunction[E, A]

  @deprecated
  def actions[A <: AggregateLike]: ActionsDeprec[A] = ActionsDeprec[A]()
  @deprecated
  def action[A <: AggregateLike]: ActionsDeprec[A] = ActionsDeprec[A]()

  @deprecated
  def Behavior[A <: AggregateLike](onCreation: => ActionsDeprec[A])(postCreation: BehaviorUnwrapped[A]): Behavior[A] = {
    case Uninitialized(_)                                              => onCreation
    case Initialized(aggregate) if postCreation.isDefinedAt(aggregate) => postCreation(aggregate)
  }

}
