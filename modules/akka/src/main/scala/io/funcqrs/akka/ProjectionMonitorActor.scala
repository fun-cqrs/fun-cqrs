package io.funcqrs.akka

import java.util.UUID

import akka.actor._
import akka.event.{ ActorEventBus, LookupClassification }
import akka.pattern.{ Backoff, BackoffSupervisor }
import io.funcqrs.CommandId
import io.funcqrs.akka.EventsMonitorActor.RemoveMe
import io.funcqrs.akka.ProjectionMonitorActor.CreateProjection
import io.funcqrs.akka.ProjectionMonitorActor.EventsMonitorRequest
import io.funcqrs.EventWithCommandId

import scala.concurrent.duration.{ FiniteDuration, _ }

object ProjectionMonitorActor {

  case class CreateProjection(props: Props, name: String)

  case class EventsMonitorRequest(commandId: CommandId, projectionName: String)

}

/**
  * Parent actor for all ProjectionActors
  */
class ProjectionMonitorActor extends Actor with ActorLogging {

  type CommandIdWithProjectionName = (CommandId, String)

  type EventWithProjectionName = (EventWithCommandId, String)

  // internal EventBus to dispatch events to subscribed EventsMonitor
  // (too lazy to build and maintain my own Map[CommandId, ActorRef])
  private val eventBus = new LookupClassification with ActorEventBus {

    type Classifier = CommandIdWithProjectionName
    type Event      = EventWithProjectionName

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
    case CreateProjection(props, name) => createNewProjection(props, name)

    // receive status from child projection
    // every incoming EventWithCommandId must be forwarded to internal EventBus
    case evt: EventWithCommandId => receivedEventFromProjection(evt)

    // create EventsMonitorActors on demand
    case EventsMonitorRequest(commandId, projectionName) => createEventMonitor(commandId, projectionName)

    // child EventsMonitor is ready, can be removed
    case RemoveMe(eventsMonitor) => removeEventMonitor(eventsMonitor)

    case anyOther => // do nothing with events without CommandId
  }

  private def createNewProjection(props: Props, name: String): Unit = {
    log.debug("initializing new projection action {}", name)

    val supervisorProps =
      BackoffSupervisor.props(
        Backoff.onStop(
          childProps = props,
          childName  = s"$name-supervised",
          // wait at least 10 seconds to restart
          minBackoff = 10.seconds, // TODO: move to config
          // wait at most 5 minutes to restart
          maxBackoff   = 5.minutes, // TODO: move to config
          randomFactor = 0.0 // TODO: move to config
        )
      )

    val projectionSupervisor = context.actorOf(supervisorProps, name)

    sender() ! projectionSupervisor // send projection actor back
  }

  private def receivedEventFromProjection(evt: Any with EventWithCommandId): Unit = {
    val currentSender = sender()

    val projectionName = currentSender.path.name
    log.debug("received {} from {}, publishing it internally", evt, currentSender)
    eventBus.publish((evt, projectionName))
  }

  private def createEventMonitor(commandId: CommandId, projectionName: String): Unit = {

    val monitorName = s"monitor::$projectionName::${commandId.value}::${UUID.randomUUID()}"

    // TODO this could be moved to property file eventually
    val shutdownEventsMonitorAfter = 10.seconds
    val monitor                    = context.actorOf(eventsMonitor(commandId, projectionName, shutdownEventsMonitorAfter), monitorName)

    // subscribe for events having `commandId` and coming from projection named with `projectionName`
    eventBus.subscribe(monitor, (commandId, projectionName))

    log.debug("Created EventMonitor {}", monitor)
    // sends monitor back
    sender() ! monitor
  }

  def removeEventMonitor(eventsMonitor: ActorRef): Unit = {
    log.debug("Removing EventMonitor {}", eventsMonitor)
    eventBus.unsubscribe(eventsMonitor)
    context.stop(eventsMonitor)
  }

  class EventsMonitorActor(commandId: CommandId, projectionName: String, timeout: FiniteDuration) extends Actor with ActorLogging {

    import EventsMonitorActor._

    var replyActor: Option[ActorRef] = None
    var eventsToWaitFor              = Seq[EventWithCommandId]()
    var eventsReceived               = Seq[EventWithCommandId]()

    //initialisation
    context.setReceiveTimeout(timeout)

    def receive: Receive = {
      case Subscribe(events) =>
        log.debug("received subscription for events {}", events)
        eventsToWaitFor = events
        replyActor      = Some(sender())
        tryComplete()

      case ReceiveTimeout =>
        log.debug("Got timeout")
        removeMe()

      case evt: EventWithCommandId =>
        log.debug("received event {}", evt)
        eventsReceived = eventsReceived :+ evt
        tryComplete()

      case anyOther => log.debug("not expecting this one!! {}", anyOther)
    }

    def tryComplete() = {
      replyActor.foreach { replyActor =>
        if ((eventsToWaitFor diff eventsReceived).isEmpty) {
          log.debug("Done, sending confirmation to {} and shutting down", replyActor)
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

object EventsMonitorActor {

  case class Subscribe(events: Seq[EventWithCommandId])

  case object Done

  case class RemoveMe(actorRef: ActorRef)

}
