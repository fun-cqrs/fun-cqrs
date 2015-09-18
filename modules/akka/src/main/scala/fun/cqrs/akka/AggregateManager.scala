package fun.cqrs.akka

import akka.actor._
import fun.cqrs.akka.AggregateActor.KillAggregate
import fun.cqrs.{Aggregate, AggregateIdentifier, Behavior, DomainCommand}

object AggregateManager {

  val maxChildren = 40
  val childrenToKillAtOnce = 20

  case class GetState(id: AggregateIdentifier)

}


/**
 * Base aggregate manager.
 * Handles communication between client and aggregate.
 * It is also capable of aggregates creation and removal.
 */
trait AggregateManager extends Actor with ActorLogging {

  import AggregateManager._

  import scala.collection.immutable._

  type AggregateType <: Aggregate

  case class PendingCommand(sender: ActorRef, targetProcessorId: AggregateType#Identifier, command: DomainCommand)

  private var childrenBeingTerminated: Set[ActorRef] = Set.empty
  private var pendingCommands: Seq[PendingCommand] = Nil


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

  def processCreation: Receive

  def processUpdate: Receive = {
    case (id: AggregateType#Identifier@unchecked, cmd: AggregateType#Protocol#UpdateCmd) => processAggregateCommand(id, cmd)
  }

  private def defaultProcessCommand: Receive = {

    case Terminated(actor)             => handleTermination(actor)
    case AggregateManager.GetState(id) => getState(id.value)

    case x => sender() ! Status.Failure(new IllegalArgumentException(s"Unknown message: $x"))
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

  /**
   * Processes aggregate command.
   * Creates an aggregate (if not already created) and handles commands caching while aggregate is being killed.
   *
   * @param aggregateId Aggregate id
   * @param command DomainCommand that should be passed to aggregate
   */
  def processAggregateCommand(aggregateId: AggregateType#Identifier, command: DomainCommand): Unit = {

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

  protected def findOrCreate(id: AggregateType#Identifier): ActorRef =
    context.child(id.value) getOrElse {
      log.debug(s"creating $id")
      create(id)
    }

  protected def create(id: AggregateType#Identifier): ActorRef = {
    killChildrenIfNecessary()
    log.debug(s"creating $id")
    val agg = context.actorOf(aggregateActorProps(id), id.value)
    context watch agg
    agg
  }

  def behavior(id: AggregateType#Identifier): Behavior[AggregateType]

  /**
   * Build Props for a new Aggregate Actor with the passed Id
   */
  def aggregateActorProps(id: AggregateType#Identifier): Props = {
    Props(classOf[AggregateActor[AggregateType]], id, behavior(id))
  }

  private def killChildrenIfNecessary() = {
    val childrenCount = context.children.size - childrenBeingTerminated.size
    if (childrenCount >= maxChildren) {
      log.debug(s"Max manager children exceeded. Killing $childrenToKillAtOnce children.")
      val childrenNotBeingTerminated = context.children.filterNot(childrenBeingTerminated.toSet)
      val childrenToKill = childrenNotBeingTerminated take childrenToKillAtOnce
      childrenToKill foreach (_ ! KillAggregate)
      childrenBeingTerminated ++= childrenToKill
    }
  }
}