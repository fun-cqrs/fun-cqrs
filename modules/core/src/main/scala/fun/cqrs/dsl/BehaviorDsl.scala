package fun.cqrs.dsl

import fun.cqrs.{Aggregate, _}

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object BehaviorDsl {

  class CreationBuilder[A <: Aggregate] {

    type Protocol = A#Protocol

    // from Cmd to Future[Event]
    type CommandToEventFuture = PartialFunction[Protocol#ProtocolCommand, Future[Protocol#ProtocolEvent]]

    // from Cmd to Event
    type CommandToEvent = PartialFunction[Protocol#ProtocolCommand, Protocol#ProtocolEvent]

    // from Cmd to Exception
    type CommandToFailure = PartialFunction[Protocol#ProtocolCommand, CommandException]

    // from Event to new Aggregate
    type EventToAggregate = PartialFunction[Protocol#ProtocolEvent, A]

    // Creation ------------------------------------------------------
    private var _acceptFunction: CommandToEventFuture = PartialFunction.empty
    private var _rejectFunction: CommandToFailure = PartialFunction.empty
    private var _handleEventFunction: EventToAggregate = PartialFunction.empty

    private[BehaviorDsl] def acceptFunction = _acceptFunction

    private[BehaviorDsl] def rejectFunction = {
      val refuseCreateFallback: CommandToFailure = {
        case cmd => new CommandException(s"Invalid command $cmd")
      }
      _rejectFunction orElse refuseCreateFallback
    }

    private[BehaviorDsl] def handleEventFunction = _handleEventFunction

    def emitsAsyncEvent(pf: CommandToEventFuture): Unit = {
      _acceptFunction = _acceptFunction orElse pf
    }

    def emitsEvent(pf: CommandToEvent): Unit = {
      emitsAsyncEvent(liftFuture(pf))
    }


    def rejectsCommands(pf: CommandToFailure): Unit = {
      _rejectFunction = _rejectFunction orElse pf
    }


    def acceptsEvents(pf: EventToAggregate): Unit = {
      _handleEventFunction = _handleEventFunction orElse pf
    }

  }

  class UpdatesBuilder[A <: Aggregate] {

    type Protocol = A#Protocol

    // from Aggregate + Cmd to Future[Event]
    type CommandToEventFuture = PartialFunction[(A, Protocol#ProtocolCommand), Future[Protocol#ProtocolEvent]]
    // from Aggregate + Cmd to Event
    type CommandToEvent = PartialFunction[(A, Protocol#ProtocolCommand), Protocol#ProtocolEvent]

    // from Aggregate + Cmd to Future[Seq[Event]]
    type CommandToEventsFuture = PartialFunction[(A, Protocol#ProtocolCommand), Future[immutable.Seq[Protocol#ProtocolEvent]]]

    // from Aggregate + Cmd to Seq[Event]
    type CommandToEvents = PartialFunction[(A, Protocol#ProtocolCommand), immutable.Seq[Protocol#ProtocolEvent]]

    // from Aggregate + Cmd to Exception
    type CommandToFailure = PartialFunction[(A, Protocol#ProtocolCommand), CommandException]

    type EventToAggregate = PartialFunction[(A, Protocol#ProtocolEvent), A]

    // Updates --------------------------------------------------------
    private var _acceptFunction: CommandToEventsFuture = PartialFunction.empty
    private var _rejectFunction: CommandToFailure = PartialFunction.empty
    private var _handleEventFunction: EventToAggregate = PartialFunction.empty


    private[BehaviorDsl] def acceptFunction = _acceptFunction

    private[BehaviorDsl] def rejectFunction = {
      val refuseUpdateFallback: CommandToFailure = {
        case (agg, cmd) => new CommandException(s"Invalid command $cmd for aggregate ${agg.identifier}")
      }
      _rejectFunction orElse refuseUpdateFallback
    }

    private[BehaviorDsl] def handleEventFunction = _handleEventFunction


    def emitsManyEventsAsync(pf: CommandToEventsFuture): Unit = {
      _acceptFunction = _acceptFunction orElse pf
    }

    def emitsSingleEventAsync(pf: CommandToEventFuture)(implicit ec: ExecutionContext): Unit = {
      emitsManyEventsAsync(mapSeq(pf))
    }

    def emitsSingleEvent(pf: CommandToEvent): Unit = {
      emitsManyEvents(liftSeq(pf))
    }

    def emitsManyEvents(pf: CommandToEvents): Unit = {
      emitsManyEventsAsync(liftFuture(pf))
    }

    def rejectsCommands(pf: CommandToFailure): Unit = {
      _rejectFunction = _rejectFunction orElse pf
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

        private val acceptCreationalCmds = creation.acceptFunction
        private val refuseCreationalCmds = creation.rejectFunction
        private val handleCreationalEvents = creation.handleEventFunction

        private val acceptCommands = updates.acceptFunction
        private val refuseCommands = updates.rejectFunction
        private val handleEvents = updates.handleEventFunction

        def validateAsync(cmd: Command)(implicit ec: ExecutionContext): Future[Event] = {
          acceptCreationalCmds
            .lift(cmd)
            .getOrElse(Future.failed(refuseCreationalCmds(cmd)))
        }

        def validateAsync(aggregate: A, cmd: Command)(implicit ec: ExecutionContext): Future[Events] = {
          acceptCommands
            .lift(aggregate, cmd)
            .getOrElse(Future.failed(refuseCommands(aggregate, cmd)))
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


  private def mapSeq[A, B](pf: PartialFunction[A, Future[B]])(implicit ec: ExecutionContext): PartialFunction[A, Future[immutable.Seq[B]]] = {
    case e if pf.isDefinedAt(e) => pf(e).map { value => immutable.Seq(value) }
  }

  private def liftFuture[A, B](pf: PartialFunction[A, B]): PartialFunction[A, Future[B]] = {
    case e if pf.isDefinedAt(e) => Future.fromTry(Try(pf(e)))
  }

  private def liftSeq[A, B](pf: PartialFunction[A, B]): PartialFunction[A, immutable.Seq[B]] = {
    case e if pf.isDefinedAt(e) => immutable.Seq(pf(e))
  }

  private def liftSeqFuture[A, B](pf: PartialFunction[A, B]): PartialFunction[A, Future[immutable.Seq[B]]] = liftFuture(liftSeq(pf))


}
