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

  /**
   * A CommandHandler is a PartialFunction from a DomainCommand to a sequence of Events within an effect F[_]
   *
   * Out of the box, we provide factories to build CommandHandler where F[_] is: the [[Identity]], a [[Try]] or a [[Future]].
   */
  type CommandHandler[C <: DomainCommand, E <: DomainEvent, F[_]] = PartialFunction[C, F[immutable.Seq[E]]]

  /**
   * An EventListener is a PartialFunction from a DomainEvent to an Aggregate
   *
   * Typically it is used to construct or update an aggregate. In case of update, an Aggregate instance must be in scope.
   */
  type EventListener[E <: DomainEvent, A <: AggregateLike] = PartialFunction[E, A]

  object Api {

    /**
     * Instantiates a [[SingleEventBinder]] for command handler returning a single Event.
     *
     * The return type of the command handler function does NOT need to be wrapped in a [[Identity]].
     * If suffices to pass a function from DomainCommand to Event.
     *
     * {{{
     *   handler { cmd: CreateLottery =>  LotteryCreated(cmd.name, metadata(id, cmd)) }
     * }}}
     *
     * @param cmdHandler - a command handler function from DomainComand to Event
     */
    def handler[C <: DomainCommand: ClassTag, E <: DomainEvent: ClassTag](cmdHandler: C => Identity[E]) = {
      // wrap single event in immutable.Seq
      val handlerWithSeq: C => Identity[immutable.Seq[E]] = (cmd: C) => immutable.Seq(cmdHandler(cmd))
      new SingleEventBinder(handlerWithSeq)(IdCommandHandlerInvoker(_))
    }

    /**
     * Returns a [[IdentityManyEventsBinderFactory]] that can be used to instantiate a [[ManyEventsBinder]]
     * for commands returning a sequence of Events.
     */
    val handler = IdentityManyEventsBinderFactory

    /** Serves as in between step for the creation of a ManyEventsBinders using a IdentityCommandHandlerInvoker */
    object IdentityManyEventsBinderFactory {

      /**
       * Instantiates a [[ManyEventsBinder]] for command handler returning many Events.
       *
       * {{{
       *   // in this example the return type of the command handler is a List[Event] which can be
       *   // automatically converted to immutable.Seq[Event]
       *   handler { cmd: CreateLottery => List(LotteryCreated(cmd.name, metadata(id, cmd))) }
       * }}}
       *
       * @param cmdHandler - a command handler function from DomainComand to immutable.Seq[Event]
       */
      def manyEvents[C <: DomainCommand: ClassTag, E <: DomainEvent: ClassTag](cmdHandler: C => Identity[immutable.Seq[E]]) = {
        new ManyEventsBinder(cmdHandler)(IdCommandHandlerInvoker(_))
      }

    }

    /**
     * Instantiates a [[SingleEventBinder]] for command handler returning a single Event wrapped in a [[Try]].
     *
     * {{{
     *   tryHandler { cmd: CreateLottery =>  Try(LotteryCreated(cmd.name, metadata(id, cmd))) }
     * }}}
     *
     * @param cmdHandler - a command handler function from DomainComand to Try[Event]
     */
    def tryHandler[C <: DomainCommand: ClassTag, E <: DomainEvent: ClassTag](cmdHandler: C => Try[E]) = {
      // wrap single event in immutable.Seq
      val handlerWithSeq: (C) => Try[immutable.Seq[E]] = (cmd: C) => cmdHandler(cmd).map(immutable.Seq(_))
      new SingleEventBinder(handlerWithSeq)(TryCommandHandlerInvoker(_))
    }

    /**
     * Returns a [[TryManyEventsBinderFactory]] that can be used to instantiate a [[ManyEventsBinder]]
     * for commands returning a sequence of Events wrapped in a [[Try]].
     */
    val tryHandler = TryManyEventsBinderFactory

    /**
     * Internal DSL API.
     * Serves as in between step for the creation of a ManyEventsBinders using a TryCommandHandlerInvoker
     */
    object TryManyEventsBinderFactory {

      /**
       * Instantiates a [[ManyEventsBinder]] for command handler returning many Events wrapped in a [[Try]].
       *
       * {{{
       *   // in this example the return type of the command handler is a List[Event] which can be
       *   // automatically converted to immutable.Seq[Event]
       *   handler { cmd: CreateLottery => Try(List(LotteryCreated(cmd.name, metadata(id, cmd)))) }
       * }}}
       *
       * @param cmdHandler - a command handler function from DomainComand to immutable.Seq[Event]
       */
      def manyEvents[C <: DomainCommand: ClassTag, E <: DomainEvent: ClassTag](cmdHandler: C => Try[immutable.Seq[E]]) = {
        new ManyEventsBinder(cmdHandler)(TryCommandHandlerInvoker(_))
      }
    }

    /**
     * Instantiates a [[SingleEventBinder]] for command handler returning a single Event wrapped in a [[Future]].
     *
     * {{{
     *   tryHandler { cmd: CreateLottery =>  Future(LotteryCreated(cmd.name, metadata(id, cmd))) }
     * }}}
     *
     * @param cmdHandler - a command handler function from DomainComand to Try[Event]
     */
    def asyncHandler[C <: DomainCommand: ClassTag, E <: DomainEvent: ClassTag](cmdHandler: C => Future[E]) = {
      import scala.concurrent.ExecutionContext.Implicits.global
      // wrap single event in immutable.Seq
      val handlerWithSeq: (C) => Future[immutable.Seq[E]] = (cmd: C) => cmdHandler(cmd).map(immutable.Seq(_))
      new SingleEventBinder(handlerWithSeq)(FutureCommandHandlerInvoker(_))
    }

    /**
     * Returns a [[TryManyEventsBinderFactory]] that can be used to instantiate a [[ManyEventsBinder]]
     * for commands returning a sequence of Events wrapped in a [[Future]].
     */
    val asyncHandler = AsyncManyEventsBinderFactory

    /**
     * Internal DSL API.
     * Serves as in between step for the creation of a ManyEventsBinders using a FutureCommandHandlerInvoker
     */
    object AsyncManyEventsBinderFactory {

      /**
       * Instantiates a [[ManyEventsBinder]] for command handler returning many Events wrapped in a [[Future]].
       *
       * {{{
       *   // in this example the return type of the command handler is a List[Event] which can be
       *   // automatically converted to immutable.Seq[Event]
       *   handler { cmd: CreateLottery => Future(List(LotteryCreated(cmd.name, metadata(id, cmd)))) }
       * }}}
       *
       * @param cmdHandler - a command handler function from DomainComand to immutable.Seq[Event]
       */
      def manyEvents[C <: DomainCommand: ClassTag, E <: DomainEvent: ClassTag](cmdHandler: C => Future[immutable.Seq[E]]) = {
        new ManyEventsBinder(cmdHandler)(FutureCommandHandlerInvoker(_))
      }
    }

    /** extractor to convert a total function into a partial function internally */
    abstract class ClassTagExtractor[T](implicit classTag: ClassTag[T]) {

      def unapply(obj: T): Option[T] = {
        // need classTag because of erasure as we must be able to find back the original type
        if (obj.getClass == classTag.runtimeClass) Some(obj)
        else None
      }
    }

    /**
     * A SingleEventBinder is a in-between step to build a [[Binding]] for a command producing one single Event.
     */
    case class SingleEventBinder[C <: DomainCommand: ClassTag, E <: DomainEvent: ClassTag, F[_]](cmdHandler: C => F[immutable.Seq[E]])(consInvoker: PartialFunction[C, F[immutable.Seq[E]]] => CommandHandlerInvoker[C, E]) {
      object CmdExtractor extends ClassTagExtractor[C]
      val cmdHandlerPF: PartialFunction[C, F[immutable.Seq[E]]] = {
        case CmdExtractor(cmd) => cmdHandler(cmd)
      }

      /**
       * Declares an event listener for the event generated by the previous defined command handler
       *
       * {{{
       *   handler { cmd: CreateLottery => LotteryCreated(cmd.name, metadata(id, cmd)) }
       *     .listener { evt => Lottery(name = evt.name, id = id) }
       * }}}
       * @param eventListener - the event listener function
       * @param evCmd - an evidence that C belongs to Aggregate's protocol
       * @param evEvt - an evidence that E belongs to Aggregate's protocol
       * @tparam A - the Aggregate type
       * @return a Biding for A
       */
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

    /**
     * A ManyEventsBinder is a in-between step to build a [[Binding]] for a command producing many Events.
     */
    case class ManyEventsBinder[C <: DomainCommand: ClassTag, E <: DomainEvent: ClassTag, F[_]](cmdHandler: C => F[immutable.Seq[E]])(consInvoker: PartialFunction[C, F[immutable.Seq[E]]] => CommandHandlerInvoker[C, E]) {

      object CmdExtractor extends ClassTagExtractor[C]
      val cmdHandlerPF: PartialFunction[C, F[immutable.Seq[E]]] = {
        case CmdExtractor(cmd) => cmdHandler(cmd)
      }

      /**
       * Declares an event listener partial function that must be defined for all Event types generated by the previous defined command handler
       *
       * {{{
       *   handler.manyEvents { cmd: Reset.type =>
       *     lottery.participants.map { name => ParticipantRemoved(name, metadata(id, cmd)) }
       *   } listener {
       *     case evt: ParticipantRemoved => lottery.removeParticipant(evt.name)
       *   }
       * }}}
       *
       * @param eventListenerPF - the event listener PartialFunction. Must be defined for all Event types produced by command handler.
       * @param evCmd - an evidence that C belongs to Aggregate's protocol
       * @param evEvt - an evidence that E belongs to Aggregate's protocol
       * @tparam A - the Aggregate type
       * @return a Biding for A
       */
      def listener[A <: AggregateLike](eventListenerPF: EventListener[E, A])(implicit evCmd: C <:< A#Command, evEvt: E <:< A#Event): Binding[A] = {
        // safe to call asInstanceOf since we have evidence that C is a A#Commnd and E is a A#Event
        Binding(
          consInvoker(cmdHandlerPF).asInstanceOf[CommandHandlerInvoker[A#Command, A#Event]],
          eventListenerPF.asInstanceOf[EventListener[A#Event, A]]
        )
      }

    }

    /**
     * A Binding express the relation between a DomainCommand, the DomainEvents it can generate and its application on a Aggregate.
     * @param cmdInvoker - the CommandHandlerInvoker that will produce one or more Events from a given DomainCommnad
     * @param eventListener - a PartialFunction from Event to Aggregate. It's used to create or update the Aggregate
     * @tparam A - the Aggregate type to which the command and events belong
     */
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

case class CreationSpec[A <: AggregateLike](
  cmdHandlerInvokerPF: PartialFunction[A#Command, CommandHandlerInvoker[A#Command, A#Event]] = PartialFunction.empty,
  eventListenerPF: PartialFunction[A#Event, A] = PartialFunction.empty
)

case class UpdateSpec[A <: AggregateLike](
  cmdHandlerInvokerPF: PartialFunction[(A, A#Command), CommandHandlerInvoker[A#Command, A#Event]] = PartialFunction.empty,
  eventListenerPF: PartialFunction[(A, A#Event), A] = PartialFunction.empty
)

case class AggregateSpec[A <: AggregateLike](creationSpec: CreationSpec[A], updateSpec: UpdateSpec[A]) extends AggregateAliases {

  type Aggregate = A

  /**
   * Declares a creational [[Binding]].
   *
   * Creational bindings define command handlers and event listeners to be called at Aggregate's instantiation.
   * Works as Factory for the Aggregate.
   *
   * @param binding - [[Binding]] for Aggregate
   * @return - an AggregateSpec configured the passed creational Binding
   */
  def whenCreating(binding: Binding[Aggregate]): AggregateSpec[Aggregate] = {

    val creationSpecUpdated =
      creationSpec.copy(
        cmdHandlerInvokerPF = creationSpec.cmdHandlerInvokerPF orElse binding.cmdInvokerPF,
        eventListenerPF = creationSpec.eventListenerPF orElse binding.eventListener
      )

    copy(creationSpec = creationSpecUpdated)
  }

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
