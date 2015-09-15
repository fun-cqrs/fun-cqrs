package fun.cqrs.dsl

import java.time.OffsetDateTime

import fun.cqrs.Aggregate
import fun.cqrs._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object BehaviorDsl {

  class CreationBuilder[A <: Aggregate] {

    type Protocol = A#Protocol

    // from Cmd to Future[CreateEvent]
    type CreateCmdToEventFuture = PartialFunction[Protocol#CreateCmd, Future[Protocol#CreateEvent]]

    // from Cmd to CreateEvent
    type CreateCmdToEvent = PartialFunction[Protocol#CreateCmd, Protocol#CreateEvent]

    // from Cmd to Exception
    type CreateCmdToFailure = PartialFunction[Protocol#CreateCmd, CommandException]

    // from Event to new Aggregate
    type CreateEventToAggregate = PartialFunction[Protocol#CreateEvent, A]

    // Creation ------------------------------------------------------
    private var _acceptFunction: CreateCmdToEventFuture = PartialFunction.empty
    private var _rejectFunction: CreateCmdToFailure = PartialFunction.empty
    private var _handleEventFunction: CreateEventToAggregate = PartialFunction.empty

    private[BehaviorDsl] def acceptFunction = _acceptFunction

    private[BehaviorDsl] def rejectFunction = {
      val refuseCreateFallback: CreateCmdToFailure = {
        case cmd => new CommandException(s"Invalid command $cmd")
      }
      _rejectFunction orElse refuseCreateFallback
    }

    private[BehaviorDsl] def handleEventFunction = _handleEventFunction

    def yieldsAsyncEvent(pf: CreateCmdToEventFuture): Unit = {
      _acceptFunction = _acceptFunction orElse pf
    }

    def yieldsEvent(pf: CreateCmdToEvent): Unit = {
      yieldsAsyncEvent(liftFuture(pf))
    }


    def rejectsCommands(pf: CreateCmdToFailure): Unit = {
      _rejectFunction = _rejectFunction orElse pf
    }


    def acceptsEvents(pf: CreateEventToAggregate): Unit = {
      _handleEventFunction = _handleEventFunction orElse pf
    }

  }

  class UpdatesBuilder[A <: Aggregate] {

    type Protocol = A#Protocol

    // from Aggregate + Cmd to Future[UpdateEvent]
    type UpdateCmdToEventFuture = PartialFunction[(A, Protocol#UpdateCmd), Future[Protocol#UpdateEvent]]
    // from Aggregate + Cmd to UpdateEvent
    type UpdateCmdToEvent = PartialFunction[(A, Protocol#UpdateCmd), Protocol#UpdateEvent]

    // from Aggregate + Cmd to Future[Seq[UpdateEvent]]
    type UpdateCmdToEventsFuture = PartialFunction[(A, Protocol#UpdateCmd), Future[Seq[Protocol#UpdateEvent]]]

    // from Aggregate + Cmd to Seq[UpdateEvent]
    type UpdateCmdToEvents = PartialFunction[(A, Protocol#UpdateCmd), Seq[Protocol#UpdateEvent]]

    // from Aggregate + Cmd to Exception
    type UpdateCmdToFailure = PartialFunction[(A, Protocol#UpdateCmd), CommandException]

    type UpdateEventToAggregate = PartialFunction[(A, Protocol#UpdateEvent), A]

    // Updates --------------------------------------------------------
    private var _acceptFunction: UpdateCmdToEventsFuture = PartialFunction.empty
    private var _rejectFunction: UpdateCmdToFailure = PartialFunction.empty
    private var _handleEventFunction: UpdateEventToAggregate = PartialFunction.empty


    private[BehaviorDsl] def acceptFunction = _acceptFunction

    private[BehaviorDsl] def rejectFunction = {
      val refuseUpdateFallback: UpdateCmdToFailure = {
        case (agg, cmd) => new CommandException(s"Invalid command $cmd for aggregate ${agg.identifier}")
      }
      _rejectFunction orElse refuseUpdateFallback
    }

    private[BehaviorDsl] def handleEventFunction = _handleEventFunction


    def yieldsManyEventsAsync(pf: UpdateCmdToEventsFuture): Unit = {
      _acceptFunction = _acceptFunction orElse pf
    }

    def yieldsSingleEventAsync(pf: UpdateCmdToEventFuture)(implicit ec: ExecutionContext): Unit = {
      yieldsManyEventsAsync(mapSeq(pf))
    }

    def yieldsSingleEvent(pf: UpdateCmdToEvent): Unit = {
      yieldsManyEvents(liftSeq(pf))
    }

    def yieldsManyEvents(pf: UpdateCmdToEvents): Unit = {
      yieldsManyEventsAsync(liftFuture(pf))
    }

    def rejectsCommands(pf: UpdateCmdToFailure): Unit = {
      _rejectFunction = _rejectFunction orElse pf
    }

    def acceptsEvents(pf: UpdateEventToAggregate): Unit = {
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

        private val acceptUpdateCmds = updates.acceptFunction
        private val refuseUpdateCmds = updates.rejectFunction
        private val handleUpdateEvents = updates.handleEventFunction

        def validate(cmd: Protocol#CreateCmd)(implicit ec: ExecutionContext): Future[Protocol#CreateEvent] = {
          acceptCreationalCmds
            .lift(cmd)
            .getOrElse(Future.failed(refuseCreationalCmds(cmd)))
        }

        def validate(aggregate: A, cmd: Protocol#UpdateCmd)(implicit ec: ExecutionContext): Future[Seq[Protocol#UpdateEvent]] = {
          acceptUpdateCmds
            .lift(aggregate, cmd)
            .getOrElse(Future.failed(refuseUpdateCmds(aggregate, cmd)))
        }

        def applyEvent(evt: Protocol#CreateEvent): A = {
          handleCreationalEvents(evt)
        }

        def applyEvent(aggregate: A, evt: Protocol#UpdateEvent): A = {
          handleUpdateEvents.lift(aggregate, evt).getOrElse(aggregate)
        }
      }
    }
  }


  def behaviorFor[A <: Aggregate]: BehaviorBuilder[A] =
    new BehaviorBuilder(new CreationBuilder, new UpdatesBuilder)


  def metadata(aggregateId: AggregateIdentifier, command: DomainCommand, tags: Tag*): Metadata = {
    Metadata(aggregateId, command.id, EventId(), OffsetDateTime.now(), tags)
  }


  private def mapSeq[A, B](pf: PartialFunction[A, Future[B]])(implicit ec: ExecutionContext): PartialFunction[A, Future[Seq[B]]] = {
    case e if pf.isDefinedAt(e) => pf(e).map { value => Seq(value) }
  }

  private def liftFuture[A, B](pf: PartialFunction[A, B]): PartialFunction[A, Future[B]] = {
    case e if pf.isDefinedAt(e) => Future.fromTry(Try(pf(e)))
  }

  private def liftSeq[A, B](pf: PartialFunction[A, B]): PartialFunction[A, Seq[B]] = {
    case e if pf.isDefinedAt(e) => Seq(pf(e))
  }

  private def liftSeqFuture[A, B](pf: PartialFunction[A, B]): PartialFunction[A, Future[Seq[B]]] = liftFuture(liftSeq(pf))


}
