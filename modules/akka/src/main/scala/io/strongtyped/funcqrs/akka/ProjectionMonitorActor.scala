package io.strongtyped.funcqrs.akka

import akka.actor._
import akka.event.{ ActorEventBus, LookupClassification }
import io.strongtyped.funcqrs.akka.EventsMonitorActor.RemoveMe
import io.strongtyped.funcqrs.akka.ProjectionMonitorActor._
import io.strongtyped.funcqrs.{ CommandId, DomainEvent }

import scala.concurrent.duration.{ FiniteDuration, _ }

/** Parent actor for all ProjectionActors
  */
class ProjectionMonitorActor extends Actor with ActorLogging {

  // internal EventBus to dispatch events to subscribed EventsMonitor
  // (too lazy to build and maintain my own Map[CommandId, ActorRef])
  private val eventBus = new LookupClassification with ActorEventBus {

    type Classifier = CommandId
    type Event = DomainEvent

    protected def mapSize(): Int = 8

    protected def publish(event: DomainEvent, subscriber: ActorRef): Unit = subscriber ! event

    protected def classify(event: DomainEvent): CommandId = event.commandId
  }

  def receive = {
    // start new projections child on demand
    case CreateProjection(props, name)   => createNewProjection(props, name)

    // receive status from child projection
    // every incoming DomainEvent must be forwarded to internal EventBus
    case evt: DomainEvent                => receivedEventFromProjection(evt)

    // create EventsMonitorActors on demand
    case EventsMonitorRequest(commandId) => createEventMonitor(commandId)

    // child EventsMonitor is ready, can be removed
    case RemoveMe(eventsMonitor)         => removeEventMonitor(eventsMonitor)

    case anyOther                        => log.warning(s"Unknown message: $anyOther")
  }

  private def createNewProjection(props: Props, name: String): Unit = {
    log.debug(s"initializing new projection action $name")
    val projectionActor = context.actorOf(props, name)
    sender() ! projectionActor // send projection actor back
  }

  private def receivedEventFromProjection(evt: DomainEvent): Unit = {
    val currentSender = sender()
    log.debug(s"received $evt from $currentSender, publishing it internally")
    eventBus.publish(evt)
  }

  private def createEventMonitor(commandId: CommandId): Unit = {

    val monitorName = s"events-monitor::${commandId.value}"

    // TODO this could be moved to property file eventually
    val shutdownEventsMonitorAfter = 10.seconds
    val monitor = context.actorOf(eventsMonitor(commandId, shutdownEventsMonitorAfter), monitorName)

    // subscribe
    eventBus.subscribe(monitor, commandId)

    log.debug(s"Created EventMonitor $monitor")
    // sends monitor back
    sender() ! monitor
  }

  def removeEventMonitor(eventsMonitor: ActorRef): Unit = {
    log.debug(s"Removing EventMonitor $eventsMonitor")
    eventBus.unsubscribe(eventsMonitor)
    context.stop(eventsMonitor)
  }

  class EventsMonitorActor(commandId: CommandId, timeout: FiniteDuration) extends Actor with ActorLogging {

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

  def eventsMonitor(commandId: CommandId, timeout: FiniteDuration) =
    Props(new EventsMonitorActor(commandId, timeout))
}

object ProjectionMonitorActor {

  case class CreateProjection(props: Props, name: String)

  case class EventsMonitorRequest(commandId: CommandId)

}

object EventsMonitorActor {

  case class Subscribe(events: Seq[DomainEvent])

  case object Done

  case class RemoveMe(actorRef: ActorRef)

}

