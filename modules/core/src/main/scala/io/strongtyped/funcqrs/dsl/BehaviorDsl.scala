package io.strongtyped.funcqrs.dsl

import io.strongtyped.funcqrs.{Aggregate, _}
import scala.collection.immutable
import scala.concurrent.{Future, ExecutionContext}

object BehaviorDsl {

  class CreationBuilder[A <: Aggregate] {

    type Protocol = A#Protocol

    sealed trait EventMagnet {
      def apply(): Future[Protocol#ProtocolEvent]
    }

    object EventMagnet {
      implicit def fromSingleEvent(event: Protocol#ProtocolEvent): EventMagnet =
        new EventMagnet {
          def apply() = Future.successful(event)
        }

      implicit def fromAsyncSingleEvent(event: Future[Protocol#ProtocolEvent])(implicit ec: ExecutionContext): EventMagnet =
        new EventMagnet {
          def apply() = event
        }

      implicit def fromException(ex: CommandException): EventMagnet =
        new EventMagnet {
          def apply() = Future.failed(ex)
        }
    }

    // from Cmd to EventMagnet
    type CommandToEventMagnet = PartialFunction[Protocol#ProtocolCommand, EventMagnet]

    // from Event to new Aggregate
    type EventToAggregate = PartialFunction[Protocol#ProtocolEvent, A]

    // Creation ------------------------------------------------------
    private var _processCommandFunction: CommandToEventMagnet = PartialFunction.empty
    private var _handleEventFunction: EventToAggregate = PartialFunction.empty

    private[BehaviorDsl] def processCommandFunction = _processCommandFunction
    
    val fallbackFunction: CommandToEventMagnet = {
      case cmd => new CommandException(s"Invalid command $cmd")
    }

    private[BehaviorDsl] def handleEventFunction = _handleEventFunction

    def processesCommands(pf: CommandToEventMagnet): Unit = {
      _processCommandFunction = _processCommandFunction orElse pf
    }

    def acceptsEvents(pf: EventToAggregate): Unit = {
      _handleEventFunction = _handleEventFunction orElse pf
    }

  }

  class UpdatesBuilder[A <: Aggregate] {

    type Protocol = A#Protocol

    sealed trait EventMagnet {
      def apply(): Future[Seq[Protocol#ProtocolEvent]]
    }

    object EventMagnet {
      implicit def fromSingleEvent(event: Protocol#ProtocolEvent): EventMagnet =
        new EventMagnet {
          def apply() = Future.successful(immutable.Seq(event))
        }

      implicit def fromEventSeq(events: immutable.Seq[Protocol#ProtocolEvent]): EventMagnet =
        new EventMagnet {
          def apply() = Future.successful(events)
        }

      implicit def fromAsyncSingleEvent(event: Future[Protocol#ProtocolEvent])
        (implicit ec: ExecutionContext): EventMagnet =
        new EventMagnet {
          def apply() = event.map(Seq(_))
        }

      implicit def fromAsyncEventSeq(events: Future[immutable.Seq[Protocol#ProtocolEvent]])
        (implicit ec: ExecutionContext): EventMagnet =
        new EventMagnet {
          def apply() = events
        }
      
      implicit def fromException(ex: CommandException): EventMagnet =
        new EventMagnet {
          def apply() = Future.failed(ex)
        }
    }

    // from Aggregate + Cmd to EventMagnet
    type CommandToEventMagnet = PartialFunction[(A, Protocol#ProtocolCommand), EventMagnet]

    type EventToAggregate = PartialFunction[(A, Protocol#ProtocolEvent), A]

    // Updates --------------------------------------------------------
    private var _processCommandFunction: CommandToEventMagnet = PartialFunction.empty
    private var _handleEventFunction: EventToAggregate = PartialFunction.empty

    val fallbackFunction: CommandToEventMagnet = {
      case (agg, cmd) => new CommandException(s"Invalid command $cmd for aggregate ${agg.id}")
    }

    private[BehaviorDsl] def processCommandFunction = _processCommandFunction

    private[BehaviorDsl] def handleEventFunction = _handleEventFunction

    def processesCommands(pf: CommandToEventMagnet): Unit = {
      _processCommandFunction = _processCommandFunction orElse pf
    }

    def acceptsEvents(pf: EventToAggregate): Unit = {
      _handleEventFunction = _handleEventFunction orElse pf
    }
  }

  class BehaviorBuilder[A <: Aggregate](val creation: CreationBuilder[A], val updates: UpdatesBuilder[A]) {

    def whenConstructing(creationBlock: CreationBuilder[A] => Unit): this.type = {
      creationBlock(creation)
      this
    }

    def whenUpdating(updateBlock: UpdatesBuilder[A] => Unit): Behavior[A] = {
      updateBlock(updates)
      build
    }


    private def build: Behavior[A] = {

      new Behavior[A] {

        private val processCreationalCmds = creation.processCommandFunction
        private val fallbackOnCreation = creation.fallbackFunction
        private val handleCreationalEvents = creation.handleEventFunction

        private val processUpdateCommands = updates.processCommandFunction
        private val fallbackOnUpdate = updates.fallbackFunction
        private val handleEvents = updates.handleEventFunction

        def validateAsync(cmd: Command)(implicit ec: ExecutionContext): Future[Event] = {
          processCreationalCmds
            .lift(cmd)
            .map(_.apply())
            .getOrElse(fallbackOnCreation(cmd).apply())
        }

        def validateAsync(cmd: Command, aggregate: A)(implicit ec: ExecutionContext): Future[Events] = {
          processUpdateCommands
            .lift(aggregate, cmd)
            .map(_.apply())
            .getOrElse(fallbackOnUpdate(aggregate, cmd).apply())
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
