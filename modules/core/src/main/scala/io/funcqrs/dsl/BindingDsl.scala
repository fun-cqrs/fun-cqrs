package io.funcqrs.dsl

import io.funcqrs._
import io.funcqrs.dsl.BindingDsl.Api.Binding
import scala.collection.immutable
import scala.concurrent.{ ExecutionContext, Future }
import scala.language.implicitConversions
import scala.reflect.ClassTag
import scala.util.Try

object BindingDsl {

  val api = Api

  object Api {

    /** A binding express the links between a command, an event and it's impact on the system
      * It holds two functions:
      * Command => Events and Event => Aggregate
      */
    case class Binding[C <: DomainCommand: ClassTag, E <: DomainEvent: ClassTag, A <: AggregateLike](eventsCons: C => Future[immutable.Seq[E]], action: E => A)

    def behaviorFor[A <: AggregateLike]: BehaviorBuilder[A] = {
      BehaviorBuilder[A](CreationBuilder(), UpdatesBuilder())
    }

    case class BindingStep[C <: DomainCommand: ClassTag, E <: DomainEvent: ClassTag](cons: C => Future[immutable.Seq[E]]) {
      def action[A <: AggregateLike](action: E => A)(implicit evCmd: C <:< A#Command, evEvt: E <:< A#Event) = {
        Binding(cons, action)
      }
    }

    def command[C <: DomainCommand: ClassTag, E <: DomainEvent: ClassTag](cons: C => Future[E]): BindingStep[C, E] = {
      import scala.concurrent.ExecutionContext.Implicits.global
      // wrap in Seq
      val consWithSeq = (cmd: C) => cons(cmd).map(evt => immutable.Seq(evt))
      BindingStep(consWithSeq)
    }

    def command = new {
      def multipleEvents[CC <: DomainCommand: ClassTag, EE <: DomainEvent: ClassTag](cons: CC => Future[immutable.Seq[EE]]): BindingStep[CC, EE] = {
        BindingStep(cons)
      }
    }

    implicit def eventToFutureEvent[E <: DomainEvent](event: E): Future[E] = Future.successful(event)

    implicit def eventToFutureEvents[E <: DomainEvent](event: immutable.Seq[E]): Future[immutable.Seq[E]] = Future.successful(event)

    implicit def tryEventToFutureEvent[E <: DomainEvent](triedEvent: Try[E]): Future[E] = Future.fromTry(triedEvent)

  }

}

case class CreationBuilder[A <: AggregateLike](processCommandFunction: PartialFunction[A#Command, Future[A#Event]] = PartialFunction.empty,
                                               handleEventFunction: PartialFunction[A#Event, A] = PartialFunction.empty) {

  val fallbackFunction: PartialFunction[A#Command, Future[A#Event]] = {
    case cmd => Future.failed(new CommandException(s"Invalid command $cmd"))
  }

}

case class UpdatesBuilder[A <: AggregateLike](processCommandFunction: PartialFunction[(A, A#Command), Future[A#Events]] = PartialFunction.empty,
                                              handleEventFunction: PartialFunction[(A, A#Event), A] = PartialFunction.empty) {

  val fallbackFunction: PartialFunction[(A, A#Command), Future[A#Events]] = {
    case (agg, cmd) => Future.failed(new CommandException(s"Invalid command $cmd for aggregate ${agg.id}"))
  }
}

case class BehaviorBuilder[A <: AggregateLike](creationBuilder: CreationBuilder[A], updatesBuilder: UpdatesBuilder[A])
    extends AggregateAliases {

  type Aggregate = A

  // from Cmd to Event
  type CommandToEvent = PartialFunction[Command, Future[Event]]
  // from Event to new Aggregate
  type EventToAggregate = PartialFunction[Event, Aggregate]

  // from Aggregate + Cmd to Seq of Events
  type UpdatesCommandToEvents = PartialFunction[(Aggregate, Command), Future[Events]]
  // from Aggregate + Event to updated Aggregate
  type UpdatesEventToAggregate = PartialFunction[(Aggregate, Event), Aggregate]

  // extractor help us to convert a total function to a partial function internally
  abstract class ClassTagExtractor[T](implicit classTag: ClassTag[T]) {

    def unapply(obj: T): Option[T] = {
      // need classTag because of erasure. We must be able to find back what the original type
      if (obj.getClass == classTag.runtimeClass) Some(obj)
      else None
    }
  }

  def whenCreating[CC <: Command: ClassTag, EE <: Event: ClassTag](binding: Binding[CC, EE, Aggregate]): BehaviorBuilder[Aggregate] = {

    object CmdExtractor extends ClassTagExtractor[CC]
    object EvtExtractor extends ClassTagExtractor[EE]

    import scala.concurrent.ExecutionContext.Implicits.global

    val commandValidationPF: CommandToEvent = {
      case CmdExtractor(cmd) => binding.eventsCons(cmd).map { evts =>
        // TODO: remove it when we have support for many events generation at construction time
        evts.head
      }
    }

    val eventApplyPF: EventToAggregate = {
      case EvtExtractor(evt) => binding.action(evt)
    }

    val creationBuilderUpdated =
      creationBuilder.copy(
        processCommandFunction = creationBuilder.processCommandFunction orElse commandValidationPF,
        handleEventFunction = creationBuilder.handleEventFunction orElse eventApplyPF
      )

    BehaviorBuilder(creationBuilderUpdated, updatesBuilder)
  }

  def whenUpdating[CC <: Command: ClassTag, EE <: Event: ClassTag](aggFun: Aggregate => Binding[CC, EE, Aggregate]): BehaviorBuilder[Aggregate] = {

    object CmdExtractor extends ClassTagExtractor[CC]
    object EvtExtractor extends ClassTagExtractor[EE]

    val commandValidationPF: UpdatesCommandToEvents = {
      case (agg, CmdExtractor(cmd)) => aggFun(agg).eventsCons(cmd)
    }

    val eventApplyPF: UpdatesEventToAggregate = {
      case (agg, EvtExtractor(evt)) => aggFun(agg).action(evt)
    }

    val updatesBuilderUpdated =
      updatesBuilder.copy(
        processCommandFunction = updatesBuilder.processCommandFunction orElse commandValidationPF,
        handleEventFunction = updatesBuilder.handleEventFunction orElse eventApplyPF
      )

    BehaviorBuilder(creationBuilder, updatesBuilderUpdated)
  }

}

object BehaviorBuilder {

  implicit def build[A <: AggregateLike](builder: BehaviorBuilder[A]): Behavior[A] = {
    import builder._
    new Behavior[A] {

      private val processCreationalCommands = creationBuilder.processCommandFunction
      private val fallbackOnCreation = creationBuilder.fallbackFunction
      private val handleCreationalEvents = creationBuilder.handleEventFunction

      private val processUpdateCommands = updatesBuilder.processCommandFunction
      private val fallbackOnUpdate = updatesBuilder.fallbackFunction
      private val handleEvents = updatesBuilder.handleEventFunction

      def validateAsync(cmd: Command)(implicit ec: ExecutionContext): Future[Event] = {
        (processCreationalCommands orElse fallbackOnCreation).apply(cmd) // apply cmd
      }

      def validateAsync(cmd: Command, aggregate: Aggregate)(implicit ec: ExecutionContext): Future[Events] = {
        (processUpdateCommands orElse fallbackOnUpdate).apply(aggregate, cmd) // apply cmd
      }

      def applyEvent(evt: Event): Aggregate = {
        handleCreationalEvents(evt)
      }

      def applyEvent(evt: Event, aggregate: Aggregate): Aggregate = {
        val handleNoEvents: UpdatesEventToAggregate = {
          case (agg, _) => agg
        }
        (handleEvents orElse handleNoEvents)(aggregate, evt)
      }

      def isEventDefined(event: Event): Boolean =
        handleCreationalEvents.isDefinedAt(event)

      def isEventDefined(event: Event, aggregate: Aggregate): Boolean =
        handleEvents.isDefinedAt(aggregate, event)
    }
  }
}

