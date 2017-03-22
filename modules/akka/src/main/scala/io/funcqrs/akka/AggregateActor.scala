package io.funcqrs.akka

import _root_.akka.actor._
import _root_.akka.pattern._
import _root_.akka.persistence._
import io.funcqrs._
import io.funcqrs.akka.util.ConfigReader._
import io.funcqrs.behavior._
import io.funcqrs.interpreters.AsyncInterpreter

import scala.concurrent.{ Future, TimeoutException }
import scala.concurrent.duration._
import scala.util.{ Success, Try }
import scala.util.control.NonFatal

object AggregateActor {

  /**
    * We don't want the aggregate to be killed if it hasn't fully restored yet,
    * thus we need some non AutoReceivedMessage that can be handled by akka persistence.
    */
  case object KillAggregate

  case class StateRequest(requester: ActorRef)

  case class Exists(requester: ActorRef)

  def props[A, C, E, I <: AggregateId](id: I, behavior: Behavior[A, C, E], parentPath: String): Props = {
    Props(new AggregateActor[A, C, E, I](id, AsyncInterpreter(behavior), parentPath))
  }
}

class AggregateActor[A, C, E, I <: AggregateId](
    identifier: I,
    interpreter: AsyncInterpreter[A, C, E],
    aggregateType: String
) extends AggregateAliases
    with AggregateMessageExtractors
    with PersistentActor
    with ActorLogging {

  type Aggregate = A
  type Id        = I
  type Command   = C
  type Event     = E

  /**
    * state of Aggregate Root
    */
  sealed trait ActorState

  case object Available extends ActorState

  case object Busy extends ActorState

  /**
    * Specifies how many events should be processed before new snapshot is taken.
    */
  private val eventsPerSnapshot =
    aggregateConfig(aggregateType).getInt("events-per-snapshot", 200)

  private val commandTimeout =
    aggregateConfig(aggregateType).getDuration("async-command-timeout", 5.seconds)

  private val dropSnapshot = aggregateConfig(aggregateType).getBoolean("drop-snapshot", default = false)

  override def preStart(): Unit = {
    super.preStart()
    if (dropSnapshot)
      deleteSnapshots(SnapshotSelectionCriteria.Latest)
  }

  override def recovery = {
    if (dropSnapshot) {
      Recovery(fromSnapshot = SnapshotSelectionCriteria.None)
    } else {
      Recovery(fromSnapshot = SnapshotSelectionCriteria.Latest)
    }
  }

  import context.dispatcher

  // persistenceId is always defined as the Aggregate.Identifier
  val persistenceId: String = identifier.value

  /** The aggregate instance wrapped in a Some if initialized, None otherwise */
  private var aggregateState: Option[Aggregate] = None

  private var eventsSinceLastSnapshot = 0

  // the sequence nr of the most recent successful snapshot
  // this is either the snapshot we recovered with, or the last confirmed successful snapshot
  // we will delete the snapshot with this sequence nr upon receiving confirmation that a new snapshot was taken
  private var currentSnapshotSequenceNr: Option[Long] = None

  /**
    * Recovery handler that receives persisted events during recovery. If a state snapshot
    * has been captured and saved, this handler will receive a [[SnapshotOffer]] message
    * followed by events that are younger than the offered snapshot.
    *
    * This handler must not have side-effects other than changing persistent actor state i.e. it
    * should not perform actions that may fail, such as interacting with external services,
    * for example.
    *
    */
  override val receiveRecover: Receive = {

    case SnapshotOffer(metadata, aggregate: Aggregate @unchecked) =>
      eventsSinceLastSnapshot = 0
      log.debug("aggregate '{}' is recovering from snapshot", identifier)
      restoreState(metadata, aggregate)

    case RecoveryCompleted =>
      log.debug("aggregate '{}' has recovered", identifier)

    case TypedEvent(event) => onEvent(event)

    case unknown => log.debug("aggregate '{}' received unknown message {} on recovery", identifier, unknown)
  }

  // always compose with defaultReceive
  override def receiveCommand: Receive = available

  private def available: Receive = {

    val receive: Receive = {

      case TypedCommand(cmd) =>
        log.debug("aggregate '{}' received cmd: {}", identifier, cmd)

        val eventualTimeout =
          after(duration = commandTimeout, using = context.system.scheduler) {
            Future.failed(new TimeoutException(s"Async command took more than $commandTimeout to complete: $cmd"))
          }

        val eventualEvents = interpreter.applyCommand(aggregateState, cmd)

        val eventWithTimeout = Future firstCompletedOf Seq(eventualEvents, eventualTimeout)

        val origSender = sender()

        eventWithTimeout map {
          case (events, nextState) => Successful(events, nextState, origSender)
        } recover {
          case NonFatal(cause) => FailedCommand(cmd, cause, origSender)
        } pipeTo self

        changeState(Busy)

    }

    // always compose with defaultReceive
    defaultReceive orElse receive

  }

  private def busy: Receive = {

    val busyReceive: Receive = {
      case Successful(events, nextState, origSender) => onSuccess(events, nextState, origSender)
      case failedCmd: FailedCommand                  => onFailure(failedCmd)
      case TypedCommand(cmd) =>
        log.debug("aggregate '{}' received {} while processing another command", identifier, cmd)
        stash()
    }

    defaultReceive orElse busyReceive

  }

  protected def defaultReceive: Receive = {
    case AggregateActor.StateRequest(requester) => sendState(requester)
    case AggregateActor.Exists(requester)       => requester ! aggregateState.isDefined
    case AggregateActor.KillAggregate           => context.stop(self)
    case x: SaveSnapshotSuccess                 =>
      // delete the previous snapshot now that we know we have a newer snapshot
      currentSnapshotSequenceNr.foreach { seqNr =>
        deleteSnapshots(SnapshotSelectionCriteria(maxSequenceNr = seqNr))
      }
      currentSnapshotSequenceNr = Some(x.metadata.sequenceNr)
  }

  /**
    * send a message containing the aggregate's state back to the requester
    *
    * @param replyTo actor to send message to
    */
  protected def sendState(replyTo: ActorRef): Unit = {
    aggregateState match {
      case Some(aggregate) =>
        log.debug("aggregate '{}' sending state to {}", identifier, replyTo)
        replyTo ! aggregate
      case None =>
        replyTo ! Status.Failure(new NoSuchElementException(s"aggregate $persistenceId have not been initialized"))
    }
  }

  protected def onEvent(evt: Any): Unit = {

    Try(evt.asInstanceOf[Event]) match {
      case Success(castedEvent) =>
        log.debug("aggregate '{}' reapplying event {}", identifier, evt)
        eventsSinceLastSnapshot += 1
        aggregateState = interpreter.onEvent(aggregateState, castedEvent)
        log.debug("aggregate '{}' has state after event {}", identifier, aggregateState)
        changeState(Available)

      case _ => log.debug(s"Unknown message on recovery $evt")
    }

  }

  /**
    * restore the lifecycle and state of the aggregate from a snapshot
    *
    * @param metadata  snapshot metadata
    * @param aggregate the aggregate
    */
  private def restoreState(metadata: SnapshotMetadata, aggregate: Aggregate) = {

    log.debug("aggregate '{}' restoring data", identifier)

    currentSnapshotSequenceNr = Some(metadata.sequenceNr)

    aggregateState = Some(aggregate)
    changeState(Available)
  }

  def changeState(state: ActorState): Unit = {
    state match {
      case Available =>
        log.debug("aggregate '{}' accepting commands", identifier)
        context become available
        unstashAll()

      case Busy =>
        log.debug("aggregate '{}' busy, only answering to GetState and command results", identifier)
        context become busy
    }
  }

  private def onSuccess(events: Events, updatedState: Option[Aggregate], origSender: ActorRef): Unit = {

    if (events.nonEmpty) {

      var eventsCount = 0

      // WATCH OUT!!!
      // procedural, state full and hard to reason piece of code!! ;-)
      persistAll(events) { evt =>
        eventsCount += 1
        eventsSinceLastSnapshot += 1

        // are we on first event?
        if (eventsCount == 1) {
          // we can send feedback to user as soon
          // as the first event comes in
          origSender ! events
        }

        // are we on last event?
        if (eventsCount == events.size) {

          // we only update the internal aggregate state once all events are persisted
          aggregateState = updatedState

          // have we crossed the snapshot threshold?
          if (eventsSinceLastSnapshot >= eventsPerSnapshot) {
            aggregateState match {
              case Some(aggregate) =>
                log.debug("aggregate '{}' has {} events reached, saving snapshot", identifier, eventsPerSnapshot)
                saveSnapshot(aggregate)
              case _ =>
            }
            eventsSinceLastSnapshot = 0
          }
        }
      }
    } else {
      // if empty, we don't persist, but we send an empty list back to sender
      origSender ! events
    }

    changeState(Available)

  }

  def onFailure(failedCmd: FailedCommand): Unit = {
    failedCmd.origSender ! Status.Failure(failedCmd.cause)

    changeState(Available)
  }

  /**
    * This method should be used as a callback handler for persist() method.
    * It will:
    * - apply the event on the aggregate effectively changing its state
    * - check if a snapshot needs to be saved.
    *
    * @param evt DomainEvent that has been persisted
    */
  protected def afterEventPersisted(evt: Event): Unit = {

    eventsSinceLastSnapshot += 1

    if (eventsSinceLastSnapshot >= eventsPerSnapshot) {
      aggregateState match {
        case Some(aggregate) =>
          log.debug("aggregate '{}' has {} events reached, saving snapshot", identifier, eventsPerSnapshot)
          saveSnapshot(aggregate)
        case _ =>
      }
      eventsSinceLastSnapshot = 0
    }
  }

  /**
    * Internal representation of a completed update command.
    */
  private case class Successful(events: Events, nextState: Option[Aggregate], origSender: ActorRef)

  private case class FailedCommand(cmd: Command, cause: Throwable, origSender: ActorRef)

}
