package io.funcqrs.akka

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
trait AggregateManager extends Actor
    with ActorLogging with AggregateAliases with AggregateMessageExtractors {

  import scala.collection.immutable._

  type Aggregate <: AggregateLike

  case class PendingCommand(sender: ActorRef, aggregateId: Id, command: Command)

  private var childrenBeingTerminated: Set[ActorRef] = Set.empty
  private var pendingCommands: Seq[PendingCommand] = Nil

  val passivationStrategy: PassivationStrategy = PassivationStrategy(self.path.name)

  log.info("passivation strategy for '{}': {}", self.path.name, passivationStrategy.toString)

  override def receive: Receive = {
    processCommand orElse defaultProcessCommand
  }

  // def processCreation: Receive
  def processCommand: Receive = {
    case IdAndCommand(id, cmd) => processAggregateCommand(id, cmd)
  }

  private def badAggregateId(id: AggregateId) = {
    sender() ! Status.Failure(new IllegalArgumentException(s"Wrong aggregate id type ${id.getClass.getSimpleName}"))
  }

  private def defaultProcessCommand: Receive = {

    case Terminated(actor) => handleTermination(actor)
    case GetState(GoodId(id)) => fetchState(id)
    case GetState(BadId(id)) => badAggregateId(id)

    case Exists(GoodId(id)) => exists(id)
    case Exists(BadId(id)) => badAggregateId(id)

    case cmd: Command =>
      log.error(
        """
           | Received command without AggregateId!
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

  def fetchState(id: Id): Unit = {
    findOrCreate(id) forward AggregateActor.StateRequest(sender())
  }

  def exists(id: Id): Unit = {
    findOrCreate(id) forward AggregateActor.Exists(sender())
  }

  private def handleTermination(actorRef: ActorRef) = {

    // remove actor from list of 'being terminated' actors
    childrenBeingTerminated = childrenBeingTerminated filterNot (_ == actorRef)

    val (commandsForChild, remainingCommands) = pendingCommands partition (_.aggregateId.value == actorRef.path.name)

    // hold remaining commands as pending
    pendingCommands = remainingCommands

    // recreate child and send buffered commands to it
    log.debug("Child termination finished. Applying {} cached commands.", commandsForChild.size)
    for (PendingCommand(commandSender, aggregateId, command) <- commandsForChild) {
      val child = findOrCreate(aggregateId)
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
      log.debug("creating {}", id)
      create(id)
    }

  protected def create(id: Id): ActorRef = {
    killChildrenIfNecessary()
    log.debug("creating {}", id)
    val agg = context.actorOf(aggregateActorProps(id), id.value)
    context watch agg
    agg
  }

  def behavior(id: Aggregate#Id): Behavior[Aggregate]

  /**
   * Build Props for a new Aggregate Actor with the passed Id
   */
  def aggregateActorProps(id: Id): Props = {
    AggregateActor.props[Aggregate](id, behavior(id), context.self.path.name)
  }

  private def killChildrenIfNecessary() =
    passivationStrategy match {
      case x: SelectionBasedPassivationStrategySupport =>
        val candidates = x.selectChildrenToKill(context.children)

        val childrenToTerminate = candidates.filterNot(childrenBeingTerminated)

        if (childrenToTerminate.nonEmpty) {
          log.debug("Max manager children exceeded. Killing {} children.", childrenToTerminate.size)
          childrenToTerminate foreach (_ ! KillAggregate)
          childrenBeingTerminated ++= childrenToTerminate
        }

      case _ =>
    }
}

class ConfigurableAggregateManager[A <: AggregateLike](behaviorCons: A#Id => Behavior[A])
    extends AggregateManager {

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