package fun.cqrs.akka

import akka.actor._
import fun.cqrs.akka.AggregateActor.KillAggregate
import fun.cqrs.{Aggregate, AggregateIdentifier, DomainCommand}

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
trait AggregateManager[A <: Aggregate] extends Actor with ActorLogging {

  import AggregateManager._

  import scala.collection.immutable._


  case class PendingCommand(sender: ActorRef, targetProcessorId: A#Identifier, command: DomainCommand)

  private var childrenBeingTerminated: Set[ActorRef] = Set.empty
  private var pendingCommands: Seq[PendingCommand] = Nil


  /**
   * Processes command.
   * In most cases it should transform message to appropriate aggregate command (and apply some additional logic if needed)
   * and call [[AggregateManager.processAggregateCommand]]
   */
  def processCommand: Receive = PartialFunction.empty

  override def receive: PartialFunction[Any, Unit] = processCommand orElse defaultProcessCommand

  def generateId: A#Identifier


  private def defaultProcessCommand: Receive = {

    case Terminated(actor)             => handleTermination(actor)
    case AggregateManager.GetState(id) => getState(id.value)

    case cmd: A#Protocol#CreateCmd                               => processAggregateCommand(generateId, cmd)
    case (id: A#Identifier@unchecked, cmd: A#Protocol#UpdateCmd) => processAggregateCommand(id, cmd)

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
  def processAggregateCommand(aggregateId: A#Identifier, command: DomainCommand): Unit = {

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

  protected def findOrCreate(id: A#Identifier): ActorRef =
    context.child(id.value) getOrElse {
      log.debug(s"creating $id")
      create(id)
    }

  protected def create(id: A#Identifier): ActorRef = {
    killChildrenIfNecessary()
    log.debug(s"creating $id")
    val agg = context.actorOf(aggregateActorProps(id), id.value)
    context watch agg
    agg
  }

  /**
   * Build Props for a new Aggregate Actor with the passed Id
   */
  def aggregateActorProps(id: A#Identifier): Props

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