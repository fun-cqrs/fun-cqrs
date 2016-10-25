package io.funcqrs.akka

import java.time.OffsetDateTime

import _root_.akka.actor._
import io.funcqrs._
import io.funcqrs.akka.AggregateActor.KillAggregate
import io.funcqrs.akka.AggregateManager.{ Exists, GetState }
import io.funcqrs.behavior.Behavior

object AggregateManager {

  case class GetState(id: AggregateId)

  case class Exists(id: AggregateId)

  case class UntypedIdAndCommand(id: AggregateId, cmd: DomainCommand)

}

/**
  * Base aggregate manager.
  * Handles communication between client and aggregate.
  * It is also capable of aggregates creation and removal.
  */
trait AggregateManager extends Actor with ActorLogging with AggregateAliases with AggregateMessageExtractors {

  import scala.collection.immutable._

  type Aggregate <: AggregateLike

  case class PendingMessage(origSender: ActorRef, message: Any)

  private var aggregateCells: List[AggregateCell] = Nil

  val passivationStrategy: PassivationStrategy = PassivationStrategy(self.path.name)

  log.info("passivation strategy for '{}': {}", self.path.name, passivationStrategy.toString)

  class AggregateCell(val aggregateId: Id, val actorRef: ActorRef) {

    private var _terminating: Boolean                       = false
    private var _lastReceiveMsgTime: Option[OffsetDateTime] = None
    private var _undeliveredMessages: List[PendingMessage]  = Nil

    def isActive               = !_terminating
    def markAsTerminating()    = _terminating = true
    def hasUndeliveredMessages = _undeliveredMessages.nonEmpty

    def undeliveredMessages = _undeliveredMessages.reverse
    def undeliveredCount    = _undeliveredMessages.size

    def forward(message: Any): Unit = {
      if (_terminating) {
        // buffer message if terminating
        log.debug("Received message for aggregate currently being killed. Adding message to cache.")
        _undeliveredMessages = PendingMessage(sender(), message) :: _undeliveredMessages
      } else {
        // forward message if active
        deliverMessage(message, sender())
      }
    }

    def deliverPendingMessage(pendingMessage: PendingMessage): Unit = {
      deliverMessage(pendingMessage.message, pendingMessage.origSender)
    }

    private def deliverMessage(message: Any, origSender: ActorRef): Unit = {
      actorRef.tell(message, origSender)
      _lastReceiveMsgTime = Some(OffsetDateTime.now())
    }
  }

  def behavior(id: Aggregate#Id): Behavior[Aggregate]

  override def receive: Receive = {

    case IdAndCommand(id, cmd) => processMessageForAggregate(id, cmd)
    case GetState(GoodId(id))  => processMessageForAggregate(id, AggregateActor.StateRequest(sender()))
    case Exists(GoodId(id))    => processMessageForAggregate(id, AggregateActor.Exists(sender()))

    case Terminated(actor)   => handleTermination(actor)
    case GetState(BadId(id)) => badAggregateId(id)
    case Exists(BadId(id))   => badAggregateId(id)

    case cmd: Command =>
      log.error(
        """
           | Received message without AggregateId!
           | {}
           |#=============================================================================#
           |# Have you configured your aggregate to use assigned IDs?                     #
           |# In that case, you must always send commands together with the aggregate ID! #
           |#=============================================================================#
         """.stripMargin,
        cmd
      )
      sender() ! Status.Failure(
        new IllegalArgumentException(s"Command send without AggregateId: $cmd!")
      )

    case x =>
      sender() ! Status.Failure(new IllegalArgumentException(s"Unknown message: $x"))
  }

  private def badAggregateId(id: AggregateId) = {
    sender() ! Status.Failure(new IllegalArgumentException(s"Wrong aggregate id type ${id.getClass.getSimpleName}"))
  }

  private def handleTermination(actorRef: ActorRef) = {

    // find terminated AggregateCell
    val oldCellOpt = aggregateCells.find(_.actorRef == actorRef)

    oldCellOpt.foreach { oldCell =>
      // remove old cell from list
      aggregateCells = aggregateCells.filterNot(_.aggregateId == oldCell.aggregateId)

      if (oldCell.hasUndeliveredMessages) {
        log.debug("Child {} termination finished. Re-delivering {} pending messages", actorRef, oldCell.undeliveredCount)

        // recreate it and send message
        val newCell = create(oldCell.aggregateId)
        oldCell.undeliveredMessages.foreach { msg =>
          newCell.deliverPendingMessage(msg)
        }
      }
    }

  }

  /**
    * Processes aggregate message.
    * Creates an aggregate (if not already created) and handles commands caching while aggregate is being killed.
    *
    */
  private def processMessageForAggregate(aggregateId: Id, message: Any) = {
    findOrCreate(aggregateId) forward message
  }

  def findOrCreate(aggregateId: Id): AggregateCell = {
    aggregateCells.find(_.aggregateId == aggregateId).getOrElse(create(aggregateId))
  }

  protected def create(id: Id): AggregateCell = {

    // first run some clean-up
    applyPassivationStrategy()

    log.debug("creating {}", id)
    val aggActorRef = context.actorOf(aggregateActorProps(id), id.value)
    context watch aggActorRef

    // add to an AggregateCell for internal management
    val newCell = new AggregateCell(id, aggActorRef)
    aggregateCells = newCell :: aggregateCells

    newCell
  }

  /**
    * Build Props for a new Aggregate Actor with the passed Id
    */
  def aggregateActorProps(id: Id): Props = {
    AggregateActor.props[Aggregate](id, behavior(id), context.self.path.name)
  }

  private def applyPassivationStrategy() =
    passivationStrategy match {
      case x: SelectionBasedPassivationStrategySupport =>
        val currentlyActive = aggregateCells.collect {
          case cell if cell.isActive => cell.actorRef
        }

        // offer active children (actorRef) to passivation strategy
        val candidates = x.selectChildrenToKill(currentlyActive)

        // retain those selected for termination
        val cellsToTerminate = aggregateCells.filter { cell =>
          candidates.exists(_ == cell.actorRef)
        }

        if (cellsToTerminate.nonEmpty) {
          log.debug("Max manager children exceeded. Killing {} children.", cellsToTerminate.size)
          terminateChildren(cellsToTerminate)
        }

      case _ =>
    }

  private def terminateChildren(cellsToTerminate: List[AggregateCell]): Unit = {
    cellsToTerminate.foreach { cell =>
      cell.actorRef ! KillAggregate
      cell.markAsTerminating()
    }
  }
}

class ConfigurableAggregateManager[A <: AggregateLike](behaviorCons: A#Id => Behavior[A]) extends AggregateManager {

  type Aggregate = A

  def behavior(id: Id): Behavior[Aggregate] = {
    behaviorCons(id)
  }
}

object ConfigurableAggregateManager {

  def props[A <: AggregateLike](behaviorCons: A#Id => Behavior[A]) = {
    Props(new ConfigurableAggregateManager(behaviorCons))
  }
}
