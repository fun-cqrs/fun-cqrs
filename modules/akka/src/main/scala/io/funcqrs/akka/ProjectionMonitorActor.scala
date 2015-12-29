package io.funcqrs.akka

import java.util.UUID

import akka.actor._
import akka.event.{ ActorEventBus, LookupClassification }
import io.funcqrs.CommandId
import io.funcqrs.akka.EventsMonitorActor.RemoveMe
import io.funcqrs.akka.EventsMonitorActor.RemoveMe
import io.funcqrs.akka.ProjectionMonitorActor.CreateProjection
import io.funcqrs.akka.ProjectionMonitorActor.EventsMonitorRequest
import io.funcqrs.akka.ProjectionMonitorActor._
import io.funcqrs.DomainEvent

import scala.concurrent.duration.{ FiniteDuration, _ }

/**
 * Parent actor for all ProjectionActors
 */
class ProjectionMonitorActor extends Actor with ActorLogging {

  type CommandIdWithProjectionName = (CommandId, String)
  // (ProjectionName, DomainEvent)
  type EventWithProjectionName = (DomainEvent, String)

  // internal EventBus to dispatch events to subscribed EventsMonitor
  // (too lazy to build and maintain my own Map[CommandId, ActorRef])
  private val eventBus = new LookupClassification with ActorEventBus {

    type Classifier = CommandIdWithProjectionName
    type Event = EventWithProjectionName

    protected def mapSize(): Int = 8

    protected def publish(eventWithName: EventWithProjectionName, subscriber: ActorRef): Unit = {
      val (evt, projectionName) = eventWithName
      subscriber ! evt
    }

    protected def classify(eventWithName: EventWithProjectionName): CommandIdWithProjectionName = {
      val (evt, projectionName) = eventWithName
      (evt.commandId, projectionName)
    }
  }

  def receive = {
    // start new projections child on demand
    case CreateProjection(props, name)                   => createNewProjection(props, name)

    // receive status from child projection
    // every incoming DomainEvent must be forwarded to internal EventBus
    case evt: DomainEvent                                => receivedEventFromProjection(evt)

    // create EventsMonitorActors on demand
    case EventsMonitorRequest(commandId, projectionName) => createEventMonitor(commandId, projectionName)

    // child EventsMonitor is ready, can be removed
    case RemoveMe(eventsMonitor)                         => removeEventMonitor(eventsMonitor)

    case anyOther                                        => log.warning(s"Unknown message: $anyOther")
  }

  private def createNewProjection(props: Props, name: String): Unit = {
    log.debug(s"initializing new projection action $name")
    val projectionActor = context.actorOf(props, name)
    sender() ! projectionActor // send projection actor back
  }

  private def receivedEventFromProjection(evt: DomainEvent): Unit = {
    val currentSender = sender()

    val projectionName = currentSender.path.name
    log.debug(s"received $evt from $currentSender, publishing it internally")
    eventBus.publish((evt, projectionName))
  }

  private def createEventMonitor(commandId: CommandId, projectionName: String): Unit = {

    val monitorName = s"monitor::$projectionName::${commandId.value}::${UUID.randomUUID()}"

    // TODO this could be moved to property file eventually
    val shutdownEventsMonitorAfter = 10.seconds
    val monitor = context.actorOf(eventsMonitor(commandId, projectionName, shutdownEventsMonitorAfter), monitorName)

    // subscribe for events having `commandId` and coming frmo projection named with `projectionName`
    eventBus.subscribe(monitor, (commandId, projectionName))

    log.debug(s"Created EventMonitor $monitor")
    // sends monitor back
    sender() ! monitor
  }

  def removeEventMonitor(eventsMonitor: ActorRef): Unit = {
    log.debug(s"Removing EventMonitor $eventsMonitor")
    eventBus.unsubscribe(eventsMonitor)
    context.stop(eventsMonitor)
  }

  class EventsMonitorActor(commandId: CommandId, projectionName: String, timeout: FiniteDuration) extends Actor with ActorLogging {

    import EventsMonitorActor._

    var replyActor: Option[ActorRef] = None
    var eventsToWaitFor = Seq[DomainEvent]()
    var eventsReceived = Seq[DomainEvent]()

    //initialisation
    context.setReceiveTimeout(timeout)

    def receive: Receive = {
      case Subscribe(events) =>
        log.debug(s"received subscription for events $events")
        eventsToWaitFor = events
        replyActor = Some(sender())
        tryComplete()

      case evt: DomainEvent =>
        log.debug(s"received event $evt")
        eventsReceived = eventsReceived :+ evt
        tryComplete()

      case ReceiveTimeout =>
        log.debug(s"Got timeout")
        removeMe()

      case anyOther => log.debug(s"not expecting this one!! $anyOther")
    }

    def tryComplete() = {
      replyActor.foreach { replyActor =>
        if ((eventsToWaitFor diff eventsReceived).isEmpty) {
          log.debug(s"Done, sending confirmation to $replyActor and shutting down")
          replyActor ! Done
          removeMe()
        }
      }
    }

    def removeMe() = {
      context.parent ! RemoveMe(self)
    }

  }

  def eventsMonitor(commandId: CommandId, projectionName: String, timeout: FiniteDuration) =
    Props(new EventsMonitorActor(commandId, projectionName, timeout))
}

object ProjectionMonitorActor {

  case class CreateProjection(props: Props, name: String)

  case class EventsMonitorRequest(commandId: CommandId, projectionName: String)

}

object EventsMonitorActor {

  case class Subscribe(events: Seq[DomainEvent])
  object Subscribe {
    def apply(event: DomainEvent): Subscribe = Subscribe(Seq(event))
  }

  case object Done

  case class RemoveMe(actorRef: ActorRef)

}

