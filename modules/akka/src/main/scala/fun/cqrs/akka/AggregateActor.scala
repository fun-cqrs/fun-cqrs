package fun.cqrs.akka

import akka.actor._
import akka.pattern.pipe
import akka.persistence._
import fun.cqrs.akka.AggregateActor._
import fun.cqrs.{Aggregate, Behavior, DomainCommand, DomainEvent}

abstract class AggregateActor[A <: Aggregate](behaviorFactory: String => Behavior[A])
  extends PersistentActor with ActorLogging {

  import context.dispatcher

  type Protocol = A#Protocol

  val behavior = behaviorFactory(persistenceId)

  /** The aggregate root instance if initialized, None otherwise */
  private var aggregateRootOpt: Option[A] = None

  /**
   * The lifecycle of the aggregate, by default [[Uninitialized]]
   */
  protected var state: State = Uninitialized

  private var eventsSinceLastSnapshot = 0

  // always compose with defaultReceive
  override def receiveCommand: Receive = initial orElse defaultReceive

  /**
   * PartialFunction to handle commands when the Actor is in the [[Uninitialized]] state
   */
  protected def initial: Receive = {
    // always compose with defaultReceive
    initialReceive orElse defaultReceive
  }

  protected def initialReceive: Receive = {

    case cmd: Protocol#CreateCmd =>
      log.debug(s"Received creation cmd: $cmd")
      val asyncResult = behavior.validate(cmd)
      val origSender = sender()

      asyncResult.map { res =>
        CompletedCreationCmd(res, origSender)
      } pipeTo self

      changeState(Busy)

  }


  /**
   * PartialFunction to handle commands when the Actor is in the [[Available]] state
   */
  protected def available: Receive = {
    // always compose with defaultReceive
    availableReceive orElse defaultReceive
  }

  protected def availableReceive: Receive = {

    case cmd: Protocol#UpdateCmd =>
      log.debug(s"Received cmd: $cmd")
      val asyncResult = behavior.validate(aggregateRootOpt.get, cmd)
      val origSender = sender()

      asyncResult.map { res =>
        CompletedUpdateCmd(res, origSender)
      } pipeTo self

      changeState(Busy)
  }

  private def busy: Receive = {
    case GetState => respond()
    case result: CompletedCreationCmd => onSuccessfulCreation(result)
    case result: CompletedUpdateCmd => handleUpdate(result)
    case _ => stash()
  }

  protected def defaultReceive: Receive = {
    case GetState => respond()
  }


  /**
   * This method should be used as a callback handler for persist() method.
   * It will:
   * - apply the event on the aggregate effectively changing its state
   * - check if a snapshot needs to be saved.
   * @param evt DomainEvent that has been persisted
   */
  protected def afterEventPersisted[E <: DomainEvent](evt: E): Unit = {

    aggregateRootOpt = applyEvent(evt)

    eventsSinceLastSnapshot += 1

    if (eventsSinceLastSnapshot >= eventsPerSnapshot) {
      log.debug(s"$eventsPerSnapshot events reached, saving snapshot")
      saveSnapshot((state, aggregateRootOpt))
      eventsSinceLastSnapshot = 0
    }
  }


  /**
   * send a message containing the aggregate's state back to the requester
   * @param replyTo actor to send message to (by default the sender from where you received a command)
   */
  protected def respond(replyTo: ActorRef = context.sender()): Unit = {
    aggregateRootOpt match {
      case Some(data) => replyTo ! data
      case None => Status.Failure(new NoSuchElementException(s"aggregate $persistenceId not initialized"))
    }
  }

  /**
   * Apply event on the AggregateRoot.
   *
   * Creational events are only applied if AggregateRoot is not yet initialized (ie: None)
   * Update events are only applied on already initialized AggregateRoots (ie: Some(root))
   *
   * All other combinations will be ignored and the current AggregateRoot state is returned.
   */
  def applyEvent(event: DomainEvent): Option[A] = {

    (aggregateRootOpt, event) match {

      // apply CreateEvent if not yet initialized
      case (None, evt: Protocol#CreateEvent) => Some(behavior.applyEvent(evt))

      // Update events are applied on current state
      case (Some(root), evt: Protocol#UpdateEvent) => Some(behavior.applyEvent(root, evt))

      // Covers:
      // (Some, CreateEvent) and (None, UpdateEvent)
      // in both cases we must ignore it and return current state
      case _ => aggregateRootOpt
    }
  }

  /**
   * Recovery handler that receives persisted events during recovery. If a state snapshot
   * has been captured and saved, this handler will receive a [[SnapshotOffer]] message
   * followed by events that are younger than the offered snapshot.
   *
   * This handler must not have side-effects other than changing persistent actor state i.e. it
   * should not perform actions that may fail, such as interacting with external services,
   * for example.
   *
   * If recovery fails, the actor will be stopped. This can be customized by
   * handling [[RecoveryFailure]].
   *
   * @see [[Recover]]
   */
  override val receiveRecover: Receive = {

    case SnapshotOffer(metadata, (state: State, data: Option[A])) =>
      eventsSinceLastSnapshot = 0
      log.debug("recovering aggregate from snapshot")
      restoreState(metadata, state, data)

    case SaveSnapshotSuccess(metadata) =>
      log.debug("snapshot saved")

    case RecoveryCompleted =>
      log.debug(s"aggregate '$persistenceId' has recovered, state = '$state'")

    case RecoveryFailure(t) =>
      throw new RuntimeException("Recovery failed, will be retried", t)

    case event: DomainEvent => onEvent(event)

    case unknown => log.debug(s"Unknown message on recovery")
  }


  protected def onEvent(evt: DomainEvent): Unit = {
    log.debug(s"Reapplying event $evt")
    eventsSinceLastSnapshot += 1
    aggregateRootOpt = applyEvent(evt)
    log.debug(s"State after event $aggregateRootOpt")
    changeState(Available)
  }

  /**
   * restore the lifecycle and state of the aggregate from a snapshot
   * @param metadata snapshot metadata
   * @param state the state of the aggregate
   * @param data the data of the aggregate
   */
  protected def restoreState(metadata: SnapshotMetadata, state: State, data: Option[A]) = {
    changeState(state)
    log.debug(s"restoring data $data")
    aggregateRootOpt = data
  }

  protected def changeState(state: State): Unit = {
    this.state = state
    this.state match {
      case Uninitialized =>
        log.debug(s"Initializing")
        context become initial
        unstashAll() // actually not need, but we never know :-)

      case Available =>
        log.debug(s"Accepting commands...")
        context become available
        unstashAll()

      case Busy =>
        log.debug(s"Busy, only answering to GetState and command results.")
        context become busy
    }
  }

  /**
   * When a Creation Command completes we must:
   * - persist the event
   * - apply the event, ie: create the aggregate
   * - notify the original sender
   */
  private def onSuccessfulCreation(result: CompletedCreationCmd): Unit = {
    handleEvent(result.event)
    result.origSender ! SuccessfulCommand(Seq(result.event))
    changeState(Available)
  }


  /**
   * When a Update Command completes we must:
   * - persist the events
   * - apply the events to the current aggregate state
   * - notify the original sender
   */
  private def handleUpdate(result: CompletedUpdateCmd): Unit = {
    result.events foreach handleEvent
    result.origSender ! SuccessfulCommand(result.events)
    changeState(Available)
  }


  private def handleEvent(evt: DomainEvent) = {
    persist(evt) { _ =>
      afterEventPersisted(evt)
    }
  }


  /**
   * Internal representation of a completed create command.
   */
  private case class CompletedCreationCmd(event: Protocol#CreateEvent, origSender: ActorRef)

  /**
   * Internal representation of a completed update command.
   */
  private case class CompletedUpdateCmd(events: Seq[Protocol#UpdateEvent], origSender: ActorRef)

}


object AggregateActor {

  /**
   * state of Aggregate Root
   */
  sealed trait State

  case object Uninitialized extends State

  case object Available extends State

  case object Busy extends State

  /**
   * We don't want the aggregate to be killed if it hasn't fully restored yet,
   * thus we need some non AutoReceivedMessage that can be handled by akka persistence.
   */
  case object KillAggregate

  case object GetState extends DomainCommand

  case class SuccessfulCommand(events: Seq[DomainEvent])


  /**
   * Specifies how many events should be processed before new snapshot is taken.
   * TODO: make configurable
   */
  val eventsPerSnapshot = 10

}