package io.funcqrs.dsl

import io.funcqrs.behavior.{ Behavior, CommandHandlerInvoker }
import io.funcqrs.{ AggregateLike, AggregateAliases }

import scala.language.implicitConversions

trait DescribeSupport {

  /**
   * Declares a creational [[Binding]].
   *
   * Creational bindings define command handlers and event listeners to be called at Aggregate's instantiation.
   * Works as Factory for the Aggregate.
   *
   * @param binding - [[Binding]] for Aggregate
   * @return - an AggregateSpec configured the passed creational Binding
   */
  def whenCreating[A <: AggregateLike](binding: Binding[A]): AggregateSpec[A] = {
    val creationSpecUpdated =
      CreationSpec(
        // full bindings must be prepended to spec PF
        cmdHandlerInvokerPF = binding.cmdHandlerInvokers,
        eventListenerPF = binding.eventListeners
      )

    AggregateSpec[A](creationSpecUpdated, UpdateSpec())
  }
}

case class AggregateSpec[A <: AggregateLike](creationSpec: CreationSpec[A], updateSpec: UpdateSpec[A]) extends AggregateAliases {

  type Aggregate = A

  /**
   * Declares an update [[Binding]].
   *
   * Update bindings define command handlers and event listeners to be called when updating an Aggregate.
   *
   * @param aggFunc - a function from Aggregate to Binding[Aggregate]. The function receives the current Aggregate instance
   *                  that can be used to validate commands and to apply the produced Events.
   * @return - an AggregateSpec configured the passed update Binding
   */
  def whenUpdating(aggFunc: Aggregate => Binding[Aggregate]): AggregateSpec[Aggregate] = {

    type UpdatesCommandToEvents = PartialFunction[(Aggregate, Command), CommandHandlerInvoker[A#Command, A#Event]]
    type UpdatesEventToAggregate = PartialFunction[(Aggregate, Event), Aggregate]

    val cmdInvokerPF: UpdatesCommandToEvents = {
      case (agg, cmd) if aggFunc(agg).cmdHandlerInvokers.isDefinedAt(cmd) => aggFunc(agg).cmdHandlerInvokers(cmd)
    }

    val eventListenerPF: UpdatesEventToAggregate = {
      case (agg, evt) if aggFunc(agg).eventListeners.isDefinedAt(evt) => aggFunc(agg).eventListeners(evt)
    }

    val updateSpecUpdated =
      updateSpec.copy(
        cmdHandlerInvokerPF = updateSpec.cmdHandlerInvokerPF orElse cmdInvokerPF,
        eventListenerPF = updateSpec.eventListenerPF orElse eventListenerPF
      )

    copy(updateSpec = updateSpecUpdated)
  }

}

object AggregateSpec {

  implicit def build[A <: AggregateLike](spec: AggregateSpec[A]): Behavior[A] = {
    new Behavior[A] {

      def commandHandlerWhenCreating: PartialFunction[Command, CommandHandlerInvoker[Command, Event]] =
        spec.creationSpec.cmdHandlerInvokerPF

      def eventListenerWhenCreating: PartialFunction[Event, Aggregate] =
        spec.creationSpec.eventListenerPF

      def commandHandlerWhenUpdating: PartialFunction[(Aggregate, Command), CommandHandlerInvoker[Command, Event]] =
        spec.updateSpec.cmdHandlerInvokerPF

      def eventListenerWhenUpdating: PartialFunction[(Aggregate, Event), Aggregate] =
        spec.updateSpec.eventListenerPF
    }
  }
}

case class CreationSpec[A <: AggregateLike](
  cmdHandlerInvokerPF: PartialFunction[A#Command, CommandHandlerInvoker[A#Command, A#Event]] = PartialFunction.empty,
  eventListenerPF: PartialFunction[A#Event, A] = PartialFunction.empty
)

case class UpdateSpec[A <: AggregateLike](
  cmdHandlerInvokerPF: PartialFunction[(A, A#Command), CommandHandlerInvoker[A#Command, A#Event]] = PartialFunction.empty,
  eventListenerPF: PartialFunction[(A, A#Event), A] = PartialFunction.empty
)