package io.funcqrs.dsl

import io.funcqrs.dsl.BindingDsl.Api.{ ManyEventsBinding, SingleEventCons, SingleEventBinding }
import io.funcqrs._
import scala.concurrent.{ ExecutionContext, Future }
import scala.language.implicitConversions
import scala.reflect.ClassTag
import scala.collection.immutable
import scala.util.Try

class BindingDsl[A <: AggregateLike] extends AggregateAliases {

  type Aggregate = A

  // from Cmd to Event
  type CommandToEvent = PartialFunction[Command, Future[Event]]
  // from Event to new Aggregate
  type EventToAggregate = PartialFunction[Event, Aggregate]

  // from Aggregate + Cmd to EventMagnet
  type UpdatesCommandToEvents = PartialFunction[(Aggregate, Command), Future[immutable.Seq[Event]]]

  type UpdatesEventToAggregate = PartialFunction[(Aggregate, Event), Aggregate]

  // extractor help us to convert a total function to a partial function internally
  abstract class Extractor[T](implicit classTag: ClassTag[T]) {
    def unapply(obj: T): Option[T] = {
      // need classTag because of erasure. We must be able to find back what the original type
      if (obj.getClass == classTag.runtimeClass) Some(obj)
      else None
    }
  }

  case class CreationBuilder(processCommandFunction: CommandToEvent = PartialFunction.empty,
                             handleEventFunction: EventToAggregate = PartialFunction.empty) {

    val fallbackFunction: CommandToEvent = {
      case cmd => Future.failed(new CommandException(s"Invalid command $cmd"))
    }

  }

  case class UpdatesBuilder(processCommandFunction: UpdatesCommandToEvents = PartialFunction.empty,
                            handleEventFunction: UpdatesEventToAggregate = PartialFunction.empty) {
    val fallbackFunction: UpdatesCommandToEvents = {
      case (agg, cmd) => Future.failed(new CommandException(s"Invalid command $cmd for aggregate ${agg.id}"))
    }
  }

  object BehaviorBuilder {

    implicit def build(builder: BehaviorBuilder): Behavior[Aggregate] = {
      import builder._
      new Behavior[Aggregate] {

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

  case class BehaviorBuilder(creationBuilder: CreationBuilder, updatesBuilder: UpdatesBuilder) {

    def whenCreating[CC <: Command: ClassTag, EE <: Event: ClassTag](binding: SingleEventBinding[CC, EE, Aggregate]): BehaviorBuilder = {

      object CmdExtractor extends Extractor[CC]
      object EvtExtractor extends Extractor[EE]

      val commandValidationPF: CommandToEvent = {
        case CmdExtractor(cmd) => binding.eventCons(cmd)
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

    def whenUpdating[CC <: Command: ClassTag, EE <: Event: ClassTag](aggFun: Aggregate => ManyEventsBinding[CC, EE, Aggregate]): BehaviorBuilder = {

      object CmdExtractor extends Extractor[CC]
      object EvtExtractor extends Extractor[EE]

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

  val api = BehaviorBuilder(CreationBuilder(), UpdatesBuilder())
}

object BindingDsl {

  val api = Api

  object Api {

    /** A binding express the links between a command, an event and it's impact on the system
      * It holds two functions:
      * Command => Event and Event => Aggregate
      */
    case class SingleEventBinding[C <: DomainCommand: ClassTag, E <: DomainEvent: ClassTag, A <: AggregateLike](eventCons: C => Future[E], action: E => A)

    case class ManyEventsBinding[C <: DomainCommand: ClassTag, E <: DomainEvent: ClassTag, A <: AggregateLike](eventsCons: C => Future[immutable.Seq[E]], action: E => A)

    object ManyEventsBinding {
      implicit def singleToManyBindingSingleEventBinding[C <: DomainCommand: ClassTag, E <: DomainEvent: ClassTag, A <: AggregateLike](binding: SingleEventBinding[C, E, A]): ManyEventsBinding[C, E, A] = {
        import scala.concurrent.ExecutionContext.Implicits.global
        // wrap in Seq
        val consWithSeq = (cmd: C) => binding.eventCons(cmd).map(evt => immutable.Seq(evt))
        ManyEventsBinding(consWithSeq, binding.action)
      }
    }

    case class SingleEventCons[C <: DomainCommand: ClassTag, E <: DomainEvent: ClassTag](cons: C => Future[E]) {
      def action[A <: AggregateLike](action: E => A)(implicit evCmd: C <:< A#Command, evEvt: E <:< A#Event) = {
        SingleEventBinding(cons, action)
      }
    }

    case class ManyEventsCons[C <: DomainCommand: ClassTag, E <: DomainEvent: ClassTag](cons: C => Future[immutable.Seq[E]]) {

      def action[A <: AggregateLike](action: E => A)(implicit evCmd: C <:< A#Command, evEvt: E <:< A#Event) = {
        ManyEventsBinding(cons, action)
      }
    }

    def command[C <: DomainCommand: ClassTag, E <: DomainEvent: ClassTag](cons: C => Future[E]): SingleEventCons[C, E] = {
      SingleEventCons(cons)
    }

    def command = new {
      def multipleEvents[CC <: DomainCommand: ClassTag, EE <: DomainEvent: ClassTag](cons: CC => Future[immutable.Seq[EE]]): ManyEventsCons[CC, EE] = {
        ManyEventsCons(cons)
      }
    }

    implicit def eventToFutureEvent[E <: DomainEvent](event: E): Future[E] = Future.successful(event)
    implicit def eventToFutureEvents[E <: DomainEvent](event: immutable.Seq[E]): Future[immutable.Seq[E]] = Future.successful(event)
    implicit def tryEventToFutureEvent[E <: DomainEvent](triedEvent: Try[E]): Future[E] = Future.fromTry(triedEvent)

  }

}
