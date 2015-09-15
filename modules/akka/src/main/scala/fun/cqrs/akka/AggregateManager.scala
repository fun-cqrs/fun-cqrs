package fun.cqrs.akka


import akka.actor._
import fun.cqrs.{Aggregate, DomainCommand}
import fun.cqrs.akka.AggregateActor.KillAggregate

object AggregateManager {

  val maxChildren = 40
  val childrenToKillAtOnce = 20

  case class PendingCommand(sender: ActorRef, targetProcessorId: String, command: DomainCommand)

  case class GetState(id: String)

}


/**
 * Base aggregate manager.
 * Handles communication between client and aggregate.
 * It is also capable of aggregates creation and removal.
 */
trait AggregateManager extends Actor with ActorLogging {

  import AggregateManager._

  import scala.collection.immutable._

  private var childrenBeingTerminated: Set[ActorRef] = Set.empty
  private var pendingCommands: Seq[PendingCommand] = Nil

  /**
   * Processes command.
   * In most cases it should transform message to appropriate aggregate command (and apply some additional logic if needed)
   * and call [[AggregateManager.processAggregateCommand]]
   */
  def processCommand: Receive

  override def receive: PartialFunction[Any, Unit] = processCommand orElse defaultProcessCommand

  private def defaultProcessCommand: Receive = {
    case Terminated(actor) => handleTermination(actor)
    case AggregateManager.GetState(id) => processAggregateCommand(id, AggregateActor.GetState)
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
  def processAggregateCommand(aggregateId: String, command: DomainCommand): Unit = {

    val maybeChild = context child aggregateId

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

  protected def findOrCreate(id: String): ActorRef =
    context.child(id) getOrElse {
      log.debug(s"creating $id")
      create(id)
    }

  protected def create(id: String): ActorRef = {
    killChildrenIfNecessary()
    log.debug(s"creating $id")
    val agg = context.actorOf(aggregateActorProps(id), id) //belangrijk om aggregateActor de juiste naam (=id) te geven
    context watch agg
    agg
  }

  /**
   * Build Props for a new Aggregate Actor with the passed Id
   */
  def aggregateActorProps(id: String): Props

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