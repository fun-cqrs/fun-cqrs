package io.strongtyped.funcqrs.dsl

import io.strongtyped.funcqrs.{Aggregate, _}

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions
import scala.util.Try

object BehaviorDsl {

  case class CreationBuilder[A <: Aggregate]
  (processCommandFunction: CreationBuilder[A]#CommandToEventMagnet = PartialFunction.empty,
   handleEventFunction: CreationBuilder[A]#EventToAggregate = PartialFunction.empty) {

    type Protocol = A#Protocol

    sealed trait EventMagnet {

      def apply(): Future[Protocol#ProtocolEvent]
    }

    object EventMagnet {

      implicit def fromSingleEvent(event: Protocol#ProtocolEvent): EventMagnet =
        new EventMagnet {
          def apply() = Future.successful(event)
        }

      implicit def fromTrySingleEvent(event: Try[Protocol#ProtocolEvent]): EventMagnet =
        new EventMagnet {
          def apply() = Future.fromTry(event)
        }

      implicit def fromAsyncSingleEvent(event: Future[Protocol#ProtocolEvent]): EventMagnet =
        new EventMagnet {
          def apply() = event
        }

      implicit def fromException(ex: Exception): EventMagnet =
        new EventMagnet {
          def apply() = Future.failed(ex)
        }
    }

    // from Cmd to EventMagnet
    type CommandToEventMagnet = PartialFunction[Protocol#ProtocolCommand, EventMagnet]

    // from Event to new Aggregate
    type EventToAggregate = PartialFunction[Protocol#ProtocolEvent, A]

    def processesCommands(pf: CommandToEventMagnet): CreationBuilder[A] = {
      copy(processCommandFunction = processCommandFunction orElse pf)
    }

    def acceptsEvents(pf: EventToAggregate): CreationBuilder[A] = {
      copy(handleEventFunction = handleEventFunction orElse pf)
    }

    val fallbackFunction: CommandToEventMagnet = {
      case cmd => new CommandException(s"Invalid command $cmd")
    }

  }

  case class UpdatesBuilder[A <: Aggregate]
  (processCommandFunction: UpdatesBuilder[A]#CommandToEventMagnet = PartialFunction.empty,
   handleEventFunction: UpdatesBuilder[A]#EventToAggregate = PartialFunction.empty) {

    private implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global
    type Protocol = A#Protocol

    sealed trait EventMagnet {

      def apply(): Future[immutable.Seq[Protocol#ProtocolEvent]]
    }

    object EventMagnet {

      implicit def fromSingleEvent(event: Protocol#ProtocolEvent): EventMagnet =
        new EventMagnet {
          def apply() = Future.successful(immutable.Seq(event))
        }

      implicit def fromImmutableEventSeq(events: immutable.Seq[Protocol#ProtocolEvent]): EventMagnet =
        new EventMagnet {
          def apply() = Future.successful(events)
        }

      implicit def fromTrySingleEvent(event: Try[Protocol#ProtocolEvent]): EventMagnet =
        new EventMagnet {
          def apply() = Future.fromTry(event.map(immutable.Seq(_)))
        }

      implicit def fromAsyncSingleEvent(event: Future[Protocol#ProtocolEvent]): EventMagnet =
        new EventMagnet {
          def apply() = event.map(immutable.Seq(_))
        }

      implicit def fromTryImmutableEventSeq(events: Try[immutable.Seq[Protocol#ProtocolEvent]]): EventMagnet =
        new EventMagnet {
          def apply() = Future.fromTry(events)
        }

      implicit def fromAsyncImmutableEventSeq(events: Future[immutable.Seq[Protocol#ProtocolEvent]]): EventMagnet =
        new EventMagnet {
          def apply() = events
        }

      implicit def fromException(ex: Exception): EventMagnet =
        new EventMagnet {
          def apply() = Future.failed(ex)
        }
    }

    // from Aggregate + Cmd to EventMagnet
    type CommandToEventMagnet = PartialFunction[(A, Protocol#ProtocolCommand), EventMagnet]

    type EventToAggregate = PartialFunction[(A, Protocol#ProtocolEvent), A]

    def processesCommands(pf: CommandToEventMagnet): UpdatesBuilder[A] = {
      copy(processCommandFunction = processCommandFunction orElse pf)
    }

    def acceptsEvents(pf: EventToAggregate): UpdatesBuilder[A] = {
      copy(handleEventFunction = handleEventFunction orElse pf)
    }

    val fallbackFunction: CommandToEventMagnet = {
      case (agg, cmd) => new CommandException(s"Invalid command $cmd for aggregate ${agg.id}")
    }
  }

  case class BehaviorBuilder[A <: Aggregate](creation: CreationBuilder[A], updates: UpdatesBuilder[A]) {

    def whenConstructing(creationBlock: CreationBuilder[A] => CreationBuilder[A]): BehaviorBuilder[A] = {
      copy(creation = creationBlock(creation))
      // this
    }

    def whenUpdating(updateBlock: UpdatesBuilder[A] => UpdatesBuilder[A]): Behavior[A] = {
      copy(updates = updateBlock(updates)).build
    }

    private def build: Behavior[A] = {

      new Behavior[A] {

        private val processCreationalCommands = creation.processCommandFunction
        private val fallbackOnCreation = creation.fallbackFunction
        private val handleCreationalEvents = creation.handleEventFunction

        private val processUpdateCommands = updates.processCommandFunction
        private val fallbackOnUpdate = updates.fallbackFunction
        private val handleEvents = updates.handleEventFunction

        def validateAsync(cmd: Command)(implicit ec: ExecutionContext): Future[Event] = {
          processCreationalCommands
            .lift(cmd)
            .getOrElse(fallbackOnCreation(cmd))
            .apply()
        }

        def validateAsync(cmd: Command, aggregate: A)(implicit ec: ExecutionContext): Future[Events] = {
          processUpdateCommands
            .lift(aggregate, cmd)
            .getOrElse(fallbackOnUpdate(aggregate, cmd))
            .apply()
        }

        def applyEvent(evt: Event): A = {
          handleCreationalEvents(evt)
        }

        def applyEvent(evt: Event, aggregate: A): A = {
          handleEvents.lift(aggregate, evt).getOrElse(aggregate)
        }
      }
    }
  }

  def behaviorFor[A <: Aggregate]: BehaviorBuilder[A] =
    new BehaviorBuilder(new CreationBuilder, new UpdatesBuilder)

}
