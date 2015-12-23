package io.funcqrs.dsl

import io.funcqrs._
import io.funcqrs.behavior._
import io.funcqrs.dsl.BindingDsl.Api.Binding
import io.funcqrs.interpreters.Identity
import scala.collection.immutable
import scala.concurrent.Future
import scala.language.{ higherKinds, implicitConversions }
import scala.reflect.ClassTag
import scala.util.Try

object BindingDsl {

  val api = Api

  type CommandHandler[C <: DomainCommand, E <: DomainEvent, F[_]] = PartialFunction[C, F[immutable.Seq[E]]]
  type EventListener[E <: DomainEvent, A <: AggregateLike] = PartialFunction[E, A]

  object Api {

    val handler = IdBinderFactory

    object IdBinderFactory {

      def apply[C <: DomainCommand: ClassTag, E <: DomainEvent: ClassTag](cmdHandler: C => Identity[E]) = {
        // wrap single event in immutable.Seq
        val handlerWithSeq: C => Identity[immutable.Seq[E]] = (cmd: C) => immutable.Seq(cmdHandler(cmd))
        new SingleEventBinder(handlerWithSeq)(IdCommandHandlerInvoker(_))
      }

      def manyEvents[C <: DomainCommand: ClassTag, E <: DomainEvent: ClassTag](cmdHandler: C => Identity[immutable.Seq[E]]) = {
        new ManyEventsBinder(cmdHandler)(IdCommandHandlerInvoker(_))
      }

    }

    val tryHandler = TryBinderFactory

    object TryBinderFactory {

      def apply[C <: DomainCommand: ClassTag, E <: DomainEvent: ClassTag](cmdHandler: C => Try[E]) = {
        // wrap single event in immutable.Seq
        val handlerWithSeq: (C) => Try[immutable.Seq[E]] = (cmd: C) => cmdHandler(cmd).map(immutable.Seq(_))
        new SingleEventBinder(handlerWithSeq)(TryCommandHandlerInvoker(_))
      }

      def manyEvents[C <: DomainCommand: ClassTag, E <: DomainEvent: ClassTag](cmdHandler: C => Try[immutable.Seq[E]]) = {
        new ManyEventsBinder(cmdHandler)(TryCommandHandlerInvoker(_))
      }
    }

    val asyncHandler = AsyncBinderFactory

    object AsyncBinderFactory {

      def apply[C <: DomainCommand: ClassTag, E <: DomainEvent: ClassTag](cmdHandler: C => Future[E]) = {
        import scala.concurrent.ExecutionContext.Implicits.global
        // wrap single event in immutable.Seq
        val handlerWithSeq: (C) => Future[immutable.Seq[E]] = (cmd: C) => cmdHandler(cmd).map(immutable.Seq(_))
        new SingleEventBinder(handlerWithSeq)(FutureCommandHandlerInvoker(_))
      }

      def manyEvents[C <: DomainCommand: ClassTag, E <: DomainEvent: ClassTag](cmdHandler: C => Future[immutable.Seq[E]]) = {
        new ManyEventsBinder(cmdHandler)(FutureCommandHandlerInvoker(_))
      }
    }

    //================================================================================================
    // extractor help us to convert a total function to a partial function internally
    abstract class ClassTagExtractor[T](implicit classTag: ClassTag[T]) {

      def unapply(obj: T): Option[T] = {
        // need classTag because of erasure as we must be able to find back the original type
        if (obj.getClass == classTag.runtimeClass) Some(obj)
        else None
      }
    }

    //================================================================================================

    case class SingleEventBinder[C <: DomainCommand: ClassTag, E <: DomainEvent: ClassTag, F[_]](cmdHandler: C => F[immutable.Seq[E]])(consInvoker: PartialFunction[C, F[immutable.Seq[E]]] => CommandHandlerInvoker[C, E]) {
      object CmdExtractor extends ClassTagExtractor[C]
      val cmdHandlerPF: PartialFunction[C, F[immutable.Seq[E]]] = {
        case CmdExtractor(cmd) => cmdHandler(cmd)
      }

      def listener[A <: AggregateLike](eventListener: E => A)(implicit evCmd: C <:< A#Command, evEvt: E <:< A#Event): Binding[A] = {

        object EvtExtractor extends ClassTagExtractor[E]
        val eventListenerPF: EventListener[E, A] = {
          case EvtExtractor(evt) => eventListener(evt)
        }

        // safe to call asInstanceOf since we have evidence that C is a A#Commnd and E is a A#Event
        Binding(
          consInvoker(cmdHandlerPF).asInstanceOf[CommandHandlerInvoker[A#Command, A#Event]],
          eventListenerPF.asInstanceOf[EventListener[A#Event, A]]
        )
      }
    }

    case class ManyEventsBinder[C <: DomainCommand: ClassTag, E <: DomainEvent: ClassTag, F[_]](cmdHandler: C => F[immutable.Seq[E]])(consInvoker: PartialFunction[C, F[immutable.Seq[E]]] => CommandHandlerInvoker[C, E]) {

      object CmdExtractor extends ClassTagExtractor[C]
      val cmdHandlerPF: PartialFunction[C, F[immutable.Seq[E]]] = {
        case CmdExtractor(cmd) => cmdHandler(cmd)
      }

      def listener[A <: AggregateLike](eventListenerPF: EventListener[E, A])(implicit evCmd: C <:< A#Command, evEvt: E <:< A#Event): Binding[A] = {
        // safe to call asInstanceOf since we have evidence that C is a A#Commnd and E is a A#Event
        Binding(
          consInvoker(cmdHandlerPF).asInstanceOf[CommandHandlerInvoker[A#Command, A#Event]],
          eventListenerPF.asInstanceOf[EventListener[A#Event, A]]
        )
      }

    }

    //================================================================================================

    //================================================================================================

    case class Binding[A <: AggregateLike](cmdInvoker: CommandHandlerInvoker[A#Command, A#Event], eventListener: EventListener[A#Event, A])
        extends AggregateAliases {

      type Aggregate = A

      def cmdInvokerPF: PartialFunction[Command, CommandHandlerInvoker[Command, Event]] = {
        case cmd if cmdInvoker.cmdHandler.isDefinedAt(cmd) => cmdInvoker
      }

    }

    def describe[A <: AggregateLike]: AggregateSpec[A] = {
      AggregateSpec[A](CreationSpec(), UpdateSpec())
    }
  }

}

case class CreationSpec[A <: AggregateLike](cmdHandlerInvokerPF: PartialFunction[A#Command, CommandHandlerInvoker[A#Command, A#Event]] = PartialFunction.empty,
                                            eventListenerPF: PartialFunction[A#Event, A] = PartialFunction.empty)

case class UpdateSpec[A <: AggregateLike](cmdHandlerInvokerPF: PartialFunction[(A, A#Command), CommandHandlerInvoker[A#Command, A#Event]] = PartialFunction.empty,
                                          eventListenerPF: PartialFunction[(A, A#Event), A] = PartialFunction.empty)

case class AggregateSpec[A <: AggregateLike](creationSpec: CreationSpec[A], updateSpec: UpdateSpec[A]) extends AggregateAliases {

  type Aggregate = A

  def whenCreating(binding: Binding[Aggregate]): AggregateSpec[Aggregate] = {

    val creationSpecUpdated =
      creationSpec.copy(
        cmdHandlerInvokerPF = creationSpec.cmdHandlerInvokerPF orElse binding.cmdInvokerPF,
        eventListenerPF = creationSpec.eventListenerPF orElse binding.eventListener
      )

    copy(creationSpec = creationSpecUpdated)
  }

  def whenUpdating(aggFunc: Aggregate => Binding[Aggregate]): AggregateSpec[Aggregate] = {

    type UpdatesCommandToEvents = PartialFunction[(Aggregate, Command), CommandHandlerInvoker[A#Command, A#Event]]
    type UpdatesEventToAggregate = PartialFunction[(Aggregate, Event), Aggregate]

    val cmdInvokerPF: UpdatesCommandToEvents = {
      case (agg, cmd) if aggFunc(agg).cmdInvokerPF.isDefinedAt(cmd) => aggFunc(agg).cmdInvoker
    }

    val eventListenerPF: UpdatesEventToAggregate = {
      case (agg, evt) if aggFunc(agg).eventListener.isDefinedAt(evt) => aggFunc(agg).eventListener(evt)
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
