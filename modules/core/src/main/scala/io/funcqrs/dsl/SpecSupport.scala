package io.funcqrs.dsl

import io.funcqrs.behavior.{ Behavior, CommandHandlerInvoker }
import io.funcqrs.{ AggregateLike, AggregateAliases }

import scala.language.implicitConversions

trait SpecSupport extends AggregateAliases {

  /**
   * Declares a creational [[Binding]].
   *
   * Creational bindings define command handlers and event listeners to be called at Aggregate's instantiation.
   * Works as Factory for the Aggregate.
   *
   * @param binding - [[Binding]] for Aggregate
   * @return - an AggregateSpec configured the passed creational Binding
   */
  //  def whenCreating[A <: AggregateLike](binding: Binding[A]): AggregateSpec[A] = {
  //    val creationSpecUpdated =
  //      CreationSpec(
  //        // full bindings must be prepended to spec PF
  //        cmdHandlerInvokerPF = binding.cmdHandlerInvokers,
  //        eventListenerPF = binding.eventListeners
  //      )
  //
  //    AggregateSpec[A](creationSpecUpdated, UpdateSpec())
  //  }

}

//case class AggregateSpec[A <: AggregateLike](creationSpec: CreationSpec[A], updateSpec: UpdateSpec[A]) extends AggregateAliases {
//
//  type Aggregate = A
//
//  /**
//    * Declares an update [[Binding]].
//    *
//    * Update bindings define command handlers and event listeners to be called when updating an Aggregate.
//    *
//    * @param aggFunc - a function from Aggregate to Binding[Aggregate]. The function receives the current Aggregate instance
//    *                that can be used to validate commands and to apply the produced Events.
//    * @return - an AggregateSpec configured the passed update Binding
//    */
//  def whenUpdating(aggFunc: Aggregate => Binding[Aggregate]): AggregateSpec[Aggregate] = {
//
//    type UpdatesCommandToEvents = PartialFunction[(Aggregate, Command), CommandHandlerInvoker[A#Command, A#Event]]
//    type UpdatesEventToAggregate = PartialFunction[(Aggregate, Event), Aggregate]
//
//    val cmdInvokerPF: UpdatesCommandToEvents = {
//      case (agg, cmd) if aggFunc(agg).cmdHandlerInvokers.isDefinedAt(cmd) => aggFunc(agg).cmdHandlerInvokers(cmd)
//    }
//
//    val eventListenerPF: UpdatesEventToAggregate = {
//      case (agg, evt) if aggFunc(agg).eventListeners.isDefinedAt(evt) => aggFunc(agg).eventListeners(evt)
//    }
//
//    val updateSpecUpdated =
//      updateSpec.copy(
//        cmdHandlerInvokerPF = updateSpec.cmdHandlerInvokerPF orElse cmdInvokerPF,
//        eventListenerPF = updateSpec.eventListenerPF orElse eventListenerPF
//      )
//
//    copy(updateSpec = updateSpecUpdated)
//  }
//
//}

case class AggregateSpec[A <: AggregateLike](spec: Spec[A]) extends AggregateAliases {

  type Aggregate = A

  /**
   * Declares an update [[Binding]].
   *
   * Update bindings define command handlers and event listeners to be called when updating an Aggregate.
   *
   * @param aggFunc - a function from Aggregate to Binding[Aggregate]. The function receives the current Aggregate instance
   *                that can be used to validate commands and to apply the produced Events.
   * @return - an AggregateSpec configured the passed update Binding
   */
  def when(aggFunc: Option[Aggregate] => Binding[Aggregate]): AggregateSpec[Aggregate] = {

    type UpdatesCommandToEvents = PartialFunction[(Option[Aggregate], Command), CommandHandlerInvoker[A#Command, A#Event]]
    type UpdatesEventToAggregate = PartialFunction[(Option[Aggregate], Event), Aggregate]

    val cmdInvokerPF: UpdatesCommandToEvents = {
      case (optionalAgg, cmd) if aggFunc(optionalAgg).cmdHandlerInvokers.isDefinedAt(cmd) => aggFunc(optionalAgg).cmdHandlerInvokers(cmd)
    }

    val eventListenerPF: UpdatesEventToAggregate = {
      case (agg, evt) if aggFunc(agg).eventListeners.isDefinedAt(evt) => aggFunc(agg).eventListeners(evt)
    }

    val specUpdated =
      new Spec[A](spec.cmdHandlerInvokerPF orElse cmdInvokerPF, spec.eventListenerPF orElse eventListenerPF)

    copy(spec = specUpdated)
  }

}

object AggregateSpec {

  def aggregateSpec[A <: AggregateLike] = AggregateSpec(new Spec[A])

  implicit def build[A <: AggregateLike](aggSpec: AggregateSpec[A]): Behavior[A] = {
    new Behavior[A] {

      def commandHandler: PartialFunction[(Option[Aggregate], Command), CommandHandlerInvoker[Command, Event]] =
        aggSpec.spec.cmdHandlerInvokerPF

      def eventListener: PartialFunction[(Option[Aggregate], Event), Aggregate] =
        aggSpec.spec.eventListenerPF
    }

  }

}

class Spec[A <: AggregateLike](
  val cmdHandlerInvokerPF: PartialFunction[(Option[A], A#Command), CommandHandlerInvoker[A#Command, A#Event]] = PartialFunction.empty,
  val eventListenerPF: PartialFunction[(Option[A], A#Event), A] = PartialFunction.empty
)