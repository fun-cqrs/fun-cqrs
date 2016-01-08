package io.funcqrs.akka

import _root_.akka.actor._
import io.funcqrs._
import io.funcqrs.akka.AggregateActor.KillAggregate
import io.funcqrs.akka.AggregateManager.{ Exists, GetState }
import io.funcqrs.behavior.Behavior

import scala.concurrent.duration.Duration

object AggregateManager {

  case class GetState(id: AggregateId)

  case class Exists(id: AggregateId)

}

case class MaxChildren(max: Int, childrenToKillAtOnce: Int)

case class AggregatePassivationStrategy(
  inactivityTimeout: Option[Duration] = None,
  maxChildren: Option[MaxChildren] = None)

/**
 * Base aggregate manager.
 * Handles communication between client and aggregate.
 * It is also capable of aggregates creation and removal.
 */
trait AggregateManager extends Actor
    with ActorLogging with AggregateAliases with AggregateIdExtractors {

  import scala.collection.immutable._

  type Aggregate <: AggregateLike

  val idStrategy: AggregateIdStrategy[Aggregate]

  case class PendingCommand(sender: ActorRef, targetProcessorId: Id, command: Command)

  private var childrenBeingTerminated: Set[ActorRef] = Set.empty
  private var pendingCommands: Seq[PendingCommand] = Nil

  def aggregatePassivationStrategy: AggregatePassivationStrategy = AggregatePassivationStrategy(maxChildren = Some(MaxChildren(40, 20)))

  /**
   * Processes command.
   * In most cases it should transform message to appropriate aggregate command (and apply some additional logic if needed)
   * and call [[AggregateManager.processAggregateCommand]]
   */
  def processCommand: Receive = PartialFunction.empty

  override def receive: PartialFunction[Any, Unit] = {
    processCommand orElse
      processCreation orElse
      processUpdate orElse
      defaultProcessCommand
  }

  def processCreation: Receive = {
    // can `any` be convert to the seed tuple (Id, Command)?
    case creationCommand if idStrategy.beforeReceiveCreateCommand.isDefinedAt(creationCommand) =>
      val (id, cmd) = idStrategy.beforeReceiveCreateCommand(creationCommand)
      id match {
        case GoodId(_) => processAggregateCommand(id, cmd)
        case _         => badAggregateId(id)
      }
  }

  // def processCreation: Receive

  def processUpdate: Receive = {
    case (GoodId(id), cmd: Command) => processAggregateCommand(id, cmd)
  }

  private def badAggregateId(id: AggregateId) = {
    sender() ! Status.Failure(new IllegalArgumentException(s"Wrong aggregate id type ${id.getClass.getSimpleName}"))
  }
  private def defaultProcessCommand: Receive = {

    case Terminated(actor)    => handleTermination(actor)
    case GetState(GoodId(id)) => fetchState(id)
    case GetState(BadId(id))  => badAggregateId(id)

    case Exists(GoodId(id))   => exists(id)
    case Exists(BadId(id))    => badAggregateId(id)

    case cmd: Command =>
      log.error(
        s"""
           | Received command without AggregateId!
           | $cmd
           |#=============================================================================#
           |# Have you configured your aggregate to use assigned IDs?                     #
           |# In that case, you must always send commands together with the aggregate ID! #
           |#=============================================================================#
         """.stripMargin)
      sender() ! Status.Failure(
        new IllegalArgumentException(s"Command send without AggregateId: $cmd!")
      )

    case x =>
      sender() ! Status.Failure(new IllegalArgumentException(s"Unknown message: $x"))
  }

  def fetchState(id: Id): Unit = {
    findOrCreate(id) forward AggregateActor.StateRequest(sender())
  }

  def exists(id: Id): Unit = {
    findOrCreate(id) forward AggregateActor.Exists(sender())
  }

  private def handleTermination(actor: ActorRef) = {
    childrenBeingTerminated = childrenBeingTerminated filterNot (_ == actor)
    val (commandsForChild, remainingCommands) = pendingCommands partition (_.targetProcessorId == actor.path.name)
    pendingCommands = remainingCommands
    log.debug("Child termination finished. Applying {} cached commands.", commandsForChild.size)
    for (PendingCommand(commandSender, targetProcessorId, command) <- commandsForChild) {
      val child = findOrCreate(targetProcessorId)
      child.tell(command, commandSender)
    }
  }

  /**
   * Processes aggregate command.
   * Creates an aggregate (if not already created) and handles commands caching while aggregate is being killed.
   *
   */
  def processAggregateCommand(aggregateId: Id, command: Command) = {

    val maybeChild = context child aggregateId.value

    maybeChild match {

      case Some(child) if childrenBeingTerminated contains child =>
        log.debug("Received command for aggregate currently being killed. Adding command to cache.")
        pendingCommands :+= PendingCommand(sender(), aggregateId, command)

      case Some(child) =>
        child forward command

      case None =>
        val child = create(aggregateId)
        child forward command
    }
  }

  protected def findOrCreate(id: Id): ActorRef =
    context.child(id.value) getOrElse {
      log.debug(s"creating $id")
      create(id)
    }

  protected def create(id: Id): ActorRef = {
    killChildrenIfNecessary()
    log.debug(s"creating $id")
    val agg = context.actorOf(aggregateActorProps(id), id.value)
    context watch agg
    agg
  }

  def behavior(id: Aggregate#Id): Behavior[Aggregate]

  /**
   * Build Props for a new Aggregate Actor with the passed Id
   */
  def aggregateActorProps(id: Id): Props = {
    Props(classOf[AggregateActor[Aggregate]], id, behavior(id), aggregatePassivationStrategy.inactivityTimeout)
  }

  private def killChildrenIfNecessary() = {
    aggregatePassivationStrategy.maxChildren.foreach {
      case MaxChildren(maxChildren, childrenToKillAtOnce) =>
        val childrenCount = context.children.size - childrenBeingTerminated.size
        if (childrenCount >= maxChildren) {
          log.debug(s"Max manager children exceeded. Killing $childrenToKillAtOnce children.")
          val childrenNotBeingTerminated = context.children.filterNot(childrenBeingTerminated)
          val childrenToKill = childrenNotBeingTerminated take childrenToKillAtOnce
          childrenToKill foreach (_ ! KillAggregate)
          childrenBeingTerminated ++= childrenToKill
        }
    }
  }

}

class ConfigurableAggregateManager[A <: AggregateLike](behaviorCons: A#Id => Behavior[A], val idStrategy: AggregateIdStrategy[A])
    extends AggregateManager {

  type Aggregate = A

  def behavior(id: Id): Behavior[Aggregate] = {
    behaviorCons(id)
  }
}

object ConfigurableAggregateManager {

  def props[A <: AggregateLike](behaviorCons: A#Id => Behavior[A], idStrategy: AggregateIdStrategy[A]) = {
    Props(new ConfigurableAggregateManager(behaviorCons, idStrategy))
  }
}