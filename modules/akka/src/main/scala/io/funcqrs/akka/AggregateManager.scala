package io.funcqrs.akka

import _root_.akka.actor._
import com.typesafe.config.{ ConfigFactory, ConfigMergeable }
import io.funcqrs._
import io.funcqrs.akka.AggregateActor.KillAggregate
import io.funcqrs.akka.AggregateManager.{ Exists, GetState }
import io.funcqrs.behavior.Behavior

import scala.concurrent.duration.Duration
import scala.util.Try
import scala.util.control.NonFatal

object AggregateManager {

  case class GetState(id: AggregateId)

  case class Exists(id: AggregateId)

  case class UntypedIdAndCommand(id: AggregateId, cmd: DomainCommand)

}

case class MaxChildren(max: Int, childrenToKillAtOnce: Int)

/**
 * Base aggregate manager.
 * Handles communication between client and aggregate.
 * It is also capable of aggregates creation and removal.
 */
trait AggregateManager extends Actor
    with ActorLogging with AggregateAliases with AggregateMessageExtractors {

  import scala.collection.immutable._

  type Aggregate <: AggregateLike

  case class PendingCommand(sender: ActorRef, targetProcessorId: Id, command: Command)

  private var childrenBeingTerminated: Set[ActorRef] = Set.empty
  private var pendingCommands: Seq[PendingCommand] = Nil

  lazy val passivationStrategy: PassivationStrategy = {
    val config = context.system.settings.config

    Try(config.getString("funcqrs.akka.passivation-strategy.class")).flatMap { configuredClassName =>
      Try {
        //laad de class
        Thread.currentThread().getContextClassLoader.loadClass(configuredClassName).newInstance().asInstanceOf[PassivationStrategy]
      }.recover {

        case e: ClassNotFoundException =>

          log.warning(
            """
              |----------------------------------------------------------------------------
              |Could not load class configured for funcqrs.akka.passivation-strategy.class.
              |Are you sure {} is correct and in your classpath?
              |
              |Falling back to default passivation strategy
              |----------------------------------------------------------------------------
            """.stripMargin, configuredClassName
          )

          new MaxChildrenPassivationStrategy()

        case _: InstantiationException | _: IllegalAccessException =>

          log.warning(
            """"
              |-----------------------------------------------------------------------------------
              |Could not instantiate the passivation strategy.
              |Are you sure {} has a default constructor and is a subclass of PassivationStrategy?
              |
              |Falling back to default passivation strategy
              |-----------------------------------------------------------------------------------
            """.stripMargin, configuredClassName
          )

          new MaxChildrenPassivationStrategy()

        case _ =>
          //class niet gevonden, laad de default
          new MaxChildrenPassivationStrategy()
      }
    }.getOrElse(new MaxChildrenPassivationStrategy)

  }

  override def receive: PartialFunction[Any, Unit] = {
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
        s"""
           | Received command without AggregateId!
           | $cmd
           |#=============================================================================#
           |# Have you configured your aggregate to use assigned IDs?                     #
           |# In that case, you must always send commands together with the aggregate ID! #
           |#=============================================================================#
         """.stripMargin
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
    Props(classOf[AggregateActor[Aggregate]], id, behavior(id), passivationStrategy.inactivityTimeout)
  }

  private def killChildrenIfNecessary() = {

    val candidates = passivationStrategy.determineChildrenToKill(context.children)

    val childrenToTerminate = candidates.filterNot(childrenBeingTerminated)

    if (childrenToTerminate.nonEmpty) {
      log.debug(s"Max manager children exceeded. Killing {} children.", childrenToTerminate.size)
      childrenToTerminate foreach (_ ! KillAggregate)
      childrenBeingTerminated ++= childrenToTerminate
    }
  }

}

/**
 * Defines a passivation strategy for aggregrate instances.
 *
 * inactivityTimeout determines how long they can idle in memory
 * determineChildrenToKill
 */
trait PassivationStrategy {

  def inactivityTimeout: Option[Duration] = None

  /**
   * Return all the children that may be killed.
   * @param candidates all the children for a given AggregateManager
   * @return
   */
  def determineChildrenToKill(candidates: Iterable[ActorRef]): Iterable[ActorRef]

}

class MaxChildrenPassivationStrategy extends PassivationStrategy {

  val maxChildren = MaxChildren(max = 40, childrenToKillAtOnce = 20)

  override def determineChildrenToKill(candidates: Iterable[ActorRef]): Iterable[ActorRef] = {
    if (candidates.size > maxChildren.max) {
      candidates.take(maxChildren.childrenToKillAtOnce)
    } else {
      Nil
    }
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