package io.funcqrs.dsl

import io.funcqrs.behavior.{ Behavior, CommandHandlerInvoker }
import io.funcqrs.{ AggregateLike, AggregateAliases }

import scala.language.implicitConversions

trait SpecSupport extends AggregateAliases {

  def behaviorOf[A <: AggregateLike] = AggregateSpec(new Spec[A])
  def aggregateSpec[A <: AggregateLike] = AggregateSpec(new Spec[A])

  trait AggregateState[+A <: AggregateLike] {
    def toOption: Option[A] = {
      this match {
        case Uninitialized => None
        case Initialized(aggregate) => Some(aggregate)
      }
    }
  }

  object AggregateState {
    def apply[A <: AggregateLike](opt: Option[A]): AggregateState[A] = {
      opt.map(Initialized(_)).getOrElse(Uninitialized)
    }
  }

  case object Uninitialized extends AggregateState[Nothing]
  case class Initialized[A <: AggregateLike](aggregate: A) extends AggregateState[A]

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
    def when(aggFunc: AggregateState[Aggregate] => Binding[Aggregate]): Behavior[Aggregate] = {

      type UpdatesCommandToEvents = PartialFunction[(Option[Aggregate], Command), CommandHandlerInvoker[A#Command, A#Event]]
      type UpdatesEventToAggregate = PartialFunction[(Option[Aggregate], Event), Aggregate]

      val func = (optAggregate: Option[Aggregate]) => {
        aggFunc(AggregateState(optAggregate))
      }

      val cmdInvokerPF: UpdatesCommandToEvents = {
        case (optionalAgg, cmd) if func(optionalAgg).cmdHandlerInvokers.isDefinedAt(cmd) => func(optionalAgg).cmdHandlerInvokers(cmd)
      }

      val rejectCmdInvokerPF: UpdatesCommandToEvents = {
        case (agg, cmd) if func(agg).rejectCmdInvokers.isDefinedAt(cmd) => func(agg).rejectCmdInvokers(cmd)
      }

      val eventListenerPF: UpdatesEventToAggregate = {
        case (agg, evt) if func(agg).eventListeners.isDefinedAt(evt) => func(agg).eventListeners(evt)
      }

      val specUpdated =
        Spec[A](
          cmdHandlerInvokerPF = spec.cmdHandlerInvokerPF orElse cmdInvokerPF,
          rejectCmdHandlerInvokerPF = spec.rejectCmdHandlerInvokerPF orElse rejectCmdInvokerPF,
          eventListenerPF = spec.eventListenerPF orElse eventListenerPF
        )

      build(copy(spec = specUpdated))
    }

    private def build(aggSpec: AggregateSpec[A]): Behavior[A] = {
      new Behavior[A] {

        def commandHandler: PartialFunction[(Option[Aggregate], Command), CommandHandlerInvoker[Command, Event]] =
          aggSpec.spec.rejectCmdHandlerInvokerPF orElse aggSpec.spec.cmdHandlerInvokerPF

        def eventListener: PartialFunction[(Option[Aggregate], Event), Aggregate] =
          aggSpec.spec.eventListenerPF
      }

    }
  }

  case class Spec[A <: AggregateLike](
    cmdHandlerInvokerPF: PartialFunction[(Option[A], A#Command), CommandHandlerInvoker[A#Command, A#Event]] = PartialFunction.empty,
    rejectCmdHandlerInvokerPF: PartialFunction[(Option[A], A#Command), CommandHandlerInvoker[A#Command, A#Event]] = PartialFunction.empty,
    eventListenerPF: PartialFunction[(Option[A], A#Event), A] = PartialFunction.empty
  )
}
