package io.funcqrs.akka

import _root_.akka.actor._
import _root_.akka.pattern.pipe
import _root_.akka.persistence._
import io.funcqrs._
import io.funcqrs.akka.util.ConfigReader._
import io.funcqrs.behavior.{ Behavior, Initialized, State, Uninitialized }
import io.funcqrs.interpreters.AsyncInterpreter

import scala.util.control.NonFatal

class AggregateActor[A <: AggregateLike](
    identifier: A#Id,
    interpreter: AsyncInterpreter[A],
    aggregateType: String
) extends AggregateAliases with PersistentActor with ActorLogging {

  type Aggregate = A

  /**
   * state of Aggregate Root
   */
  sealed trait ActorState

  case object Available extends ActorState

  case object Busy extends ActorState

  /**
   * Specifies how many events should be processed before new snapshot is taken.
   */
  val eventsPerSnapshot =
    aggregateConfig(aggregateType).getInt("events-per-snapshot", 200)

  import context.dispatcher

  // persistenceId is always defined as the Aggregate.Identifier
  val persistenceId = identifier.value

  /** The aggregate instance if initialized, Uninitialized otherwise */
  private var aggregateState: State[Aggregate] = Uninitialized(identifier)

  private var eventsSinceLastSnapshot = 0

  // the sequence nr of the most recent successfull snapshot
  // this is either the snapshot we recovered with, or the last confirmed successfull snapshot
  // we will delete the snapshot with this sequencenr upon receiving confirmation that a new snapshot was taken
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
      log.debug("recovering aggregate from snapshot")
      restoreState(metadata, aggregate)

    case RecoveryCompleted =>
      log.debug("aggregate '{}' has recovered", identifier)

    case event: Event => onEvent(event)

    case unknown => log.debug("Unknown message on recovery")
  }

  // always compose with defaultReceive
  override def receiveCommand: Receive = available

  private def available: Receive = {

    val receive: Receive = {

      case cmd: Command =>
        log.debug("Received cmd: {}", cmd)
        val eventualEvents = interpreter.onCommand(aggregateState, cmd)
        val origSender = sender()

        eventualEvents map {
          events => Successful(events, origSender)
        } recover {
          case NonFatal(cause) => FailedCommand(cause, origSender)
        } pipeTo self

        changeState(Busy)

    }

    // always compose with defaultReceive
    receive orElse defaultReceive

  }

  private def busy: Receive = {

    val busyReceive: Receive = {

      case AggregateActor.StateRequest(requester) => sendState(requester)
      case Successful(events, origSender) => onSuccess(events, origSender)
      case failedCmd: FailedCommand => onFailure(failedCmd)

      case cmd: Command =>
        log.debug("received {} while processing another command", cmd)
        stash()
    }

    busyReceive orElse defaultReceive

  }

  def onFailure(failedCmd: FailedCommand): Unit = {
    failedCmd.origSender ! Status.Failure(failedCmd.cause)
    changeState(Available)
  }

  protected def defaultReceive: Receive = {
    case AggregateActor.StateRequest(requester) => sendState(requester)
    case AggregateActor.Exists(requester) => requester ! aggregateState.isInitialized
    case AggregateActor.KillAggregate => context.stop(self)
    case x: SaveSnapshotSuccess =>
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
      case Initialized(aggregate) =>
        log.debug("sending aggregate {} to {}", aggregate.id, replyTo)
        replyTo ! aggregate
      case Uninitialized(id) =>
        replyTo ! Status.Failure(new NoSuchElementException(s"aggregate $id not initialized"))
    }
  }

  /**
   * Apply event on the AggregateRoot.
   */
  def applyEvent(event: Event): State[Aggregate] =
    interpreter.onEvent(aggregateState, event)

  protected def onEvent(evt: Event): Unit = {
    log.debug("Reapplying event {}", evt)
    eventsSinceLastSnapshot += 1
    aggregateState = applyEvent(evt)
    log.debug("State after event {}", aggregateState)
    changeState(Available)
  }

  /**
   * restore the lifecycle and state of the aggregate from a snapshot
   *
   * @param metadata  snapshot metadata
   * @param aggregate the aggregate
   */
  protected def restoreState(metadata: SnapshotMetadata, aggregate: Aggregate) = {
    log.debug("restoring data for aggregate {}", aggregate.id)

    currentSnapshotSequenceNr = Some(metadata.sequenceNr)

    aggregateState = Initialized(aggregate)
    changeState(Available)
  }

  def changeState(state: ActorState): Unit = {
    state match {
      case Available =>
        log.debug("Accepting commands...")
        context become available
        unstashAll()

      case Busy =>
        log.debug("Busy, only answering to GetState and command results.")
        context become busy
    }
  }

  private def onSuccess(events: Events, origSender: ActorRef): Unit = {

    if (events.nonEmpty) {
      persistAll(events) { evt => afterEventPersisted(evt) }
    }
    origSender ! events
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

    aggregateState = applyEvent(evt)
    eventsSinceLastSnapshot += 1

    if (eventsSinceLastSnapshot >= eventsPerSnapshot) {
      aggregateState match {
        case Initialized(aggregate) =>
          log.debug("{} events reached, saving snapshot", eventsPerSnapshot)
          saveSnapshot(aggregate)
        case _ =>
      }
      eventsSinceLastSnapshot = 0
    }
  }

  /**
   * Internal representation of a completed update command.
   */
  private case class Successful(events: Events, origSender: ActorRef)

  private case class FailedCommand(cause: Throwable, origSender: ActorRef)

}

object AggregateActor {

  /**
   * We don't want the aggregate to be killed if it hasn't fully restored yet,
   * thus we need some non AutoReceivedMessage that can be handled by akka persistence.
   */
  case object KillAggregate

  case class StateRequest(requester: ActorRef)

  case class Exists(requester: ActorRef)

  def props[A <: AggregateLike](id: A#Id, behavior: Behavior[A], parentPath: String) = {
    Props(new AggregateActor(id, AsyncInterpreter(behavior), parentPath))
  }
}

/**
 * Exceptions extending this trait will not get logged by FunCqrs as errors.
 */
trait DomainException { self: Throwable => }
