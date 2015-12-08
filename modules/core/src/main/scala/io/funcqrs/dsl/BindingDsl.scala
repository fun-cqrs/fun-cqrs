package io.funcqrs.dsl

import io.funcqrs.dsl.BindingDsl.Api.{UpdateBinding, CreateEventCons, CreateBinding}
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

        def isEventDefined(event: Event, aggregate: Aggregate): Boolean = false

        //        handleEvents.isDefinedAt(aggregate, event)
      }
    }
  }

  case class BehaviorBuilder(creationBuilder: CreationBuilder, updatesBuilder: UpdatesBuilder) {

    def bind[CC <: Command: ClassTag, EE <: Event: ClassTag](binding: CreateBinding[CC, EE, Aggregate]): BehaviorBuilder = {

      object CmdExtractor extends Extractor[CC]
      object EvtExtractor extends Extractor[EE]

      val commandValidationPF: CommandToEvent = {
        case CmdExtractor(cmd) => binding.cons(cmd)
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

    def bind[CC <: Command: ClassTag, EE <: Event: ClassTag](aggFun: Aggregate => UpdateBinding[CC, EE, Aggregate]): BehaviorBuilder = {

      object CmdExtractor extends Extractor[CC]
      object EvtExtractor extends Extractor[EE]

      val commandValidationPF: UpdatesCommandToEvents = {
        case (agg, CmdExtractor(cmd)) => aggFun(agg).cons(cmd)
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
    case class CreateBinding[CC <: DomainCommand: ClassTag, EE <: DomainEvent: ClassTag, A <: AggregateLike](cons: CC => Future[EE], action: EE => A)

    case class UpdateBinding[CC <: DomainCommand: ClassTag, EE <: DomainEvent: ClassTag, A <: AggregateLike](cons: CC => Future[immutable.Seq[EE]], action: EE => A)

    case class CreateEventCons[CC <: DomainCommand: ClassTag, EE <: DomainEvent: ClassTag](cons: CC => Future[EE]) {

      def action[A <: AggregateLike](action: EE => A)(implicit ev: EE <:< A#Event, ev2: CC <:< A#Command) = {
        CreateBinding(cons, action)
      }
    }

    case class UpdateEventsCons[CC <: DomainCommand: ClassTag, EE <: DomainEvent: ClassTag](cons: CC => Future[immutable.Seq[EE]]) {

      def action[A <: AggregateLike](action: EE => A)(implicit ev: EE <:< A#Event) = {
        UpdateBinding(cons, action)
      }
    }

    def createCommand[CC <: DomainCommand: ClassTag, EE <: DomainEvent: ClassTag](cons: CC => Future[EE]): CreateEventCons[CC, EE] = {
      CreateEventCons(cons)
    }

    def updateCommand[CC <: DomainCommand: ClassTag, EE <: DomainEvent: ClassTag](cons: CC => Future[EE]): UpdateEventsCons[CC, EE] = {
      import scala.concurrent.ExecutionContext.Implicits.global
      // wrap in Seq
      val consWithSeq = (cmd:CC) => cons(cmd).map(evt => immutable.Seq(evt))
      UpdateEventsCons(consWithSeq)
    }

    implicit def eventToFutureEvent[EE <: DomainEvent](event: EE): Future[EE] = Future.successful(event)
    implicit def tryEventToFutureEvent[EE <: DomainEvent](triedEvent: Try[EE]): Future[EE] = Future.fromTry(triedEvent)

  }

}
