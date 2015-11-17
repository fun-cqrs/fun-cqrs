package io.strongtyped.funcqrs.akka

import akka.actor._
import io.strongtyped.funcqrs.akka.AggregateActor.KillAggregate
import io.strongtyped.funcqrs.{AggregateAliases, AggregateDef, AggregateID, Behavior}
import scala.concurrent.duration.Duration

object AggregateManager {
  case class GetState(id: AggregateID)
}

case class MaxChildren(max: Int, childrenToKillAtOnce: Int)

case class AggregatePassivationStrategy(
  inactivityTimeout: Option[Duration] = None,
  maxChildren: Option[MaxChildren] = None)

/** Base aggregate manager.
  * Handles communication between client and aggregate.
  * It is also capable of aggregates creation and removal.
  */
trait AggregateManager extends Actor with ActorLogging with AggregateAliases {

  import scala.collection.immutable._

   type Aggregate <: AggregateDef

  case class PendingCommand(sender: ActorRef, targetProcessorId: Id, command: Command)

  private var childrenBeingTerminated: Set[ActorRef] = Set.empty
  private var pendingCommands: Seq[PendingCommand] = Nil

  def aggregatePassivationStrategy: AggregatePassivationStrategy

  /** Processes command.
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

  def processCreation: Receive

  def processUpdate: Receive = {
    case (id: Id @unchecked, cmd: Command) => processAggregateCommand(id, cmd)
  }

  private def defaultProcessCommand: Receive = {

    case Terminated(actor)             => handleTermination(actor)
    case AggregateManager.GetState(id) => getState(id.value)

    case x                             => sender() ! Status.Failure(new IllegalArgumentException(s"Unknown message: $x"))
  }

  def getState(id: String) = {

    val maybeChild = context child id

    maybeChild match {
      case Some(child) => child forward AggregateActor.GetState
      case None        => Status.Failure(new NoSuchElementException(s"aggregate $id not initialized"))
    }
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

  /** Processes aggregate command.
    * Creates an aggregate (if not already created) and handles commands caching while aggregate is being killed.
    *
    * @param aggregateId Aggregate id
    * @param command DomainCommand that should be passed to aggregate
    */
  def processAggregateCommand(aggregateId: Id, command: Command): Unit = {

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

  def behavior(id: Id): Behavior[Aggregate]

  /** Build Props for a new Aggregate Actor with the passed Id
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