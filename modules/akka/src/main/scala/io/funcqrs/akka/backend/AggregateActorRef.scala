package io.funcqrs.akka.backend

import _root_.akka.actor.ActorRef
import _root_.akka.actor.Actor
import _root_.akka.pattern.{ ask => akkaAsk }
import _root_.akka.util.Timeout
import io.funcqrs._
import io.funcqrs.akka.AggregateManager.{ Exists, GetState, UntypedIdAndCommand }
import io.funcqrs.akka.EventsMonitorActor.Subscribe
import io.funcqrs.akka.{ EventsMonitorActor, ProjectionMonitorActor }
import io.funcqrs.behavior.AggregateAliases

import scala.concurrent.Future
import scala.concurrent.duration.{ FiniteDuration, _ }
import scala.util.Try
import scala.util.control.NonFatal
import scala.collection.immutable

case class AggregateActorRef[A, C, E, I <: AggregateId](
    id: I,
    aggregateManagerActor: ActorRef,
    projectionMonitor: ActorRef,
    timeoutDuration: FiniteDuration = 5.seconds
) extends AsyncAggregateRef[A, C, E]
    with AggregateAliases {

  type Aggregate = A
  type Id        = I
  type Command   = C
  type Event     = E

  def askTimeout = Timeout(timeoutDuration)

  def withAskTimeout(timeout: FiniteDuration): AggregateRef[A, C, E, Future] =
    copy(timeoutDuration = timeout)

  // need it explicitly because akka.pattern.ask conflicts with AggregatRef.ask
  private val askableActorRef = akkaAsk(aggregateManagerActor)

  //if you need a reply w/o using ask (more idiomatic to do so)
  def tell(cmd: Command, sender: ActorRef): Unit =
    aggregateManagerActor.tell(UntypedIdAndCommand(id, cmd), sender)

  def tell(cmd: Command): Unit =
    aggregateManagerActor ! UntypedIdAndCommand(id, cmd)

  def ask(cmd: Command): Future[Events] = {
    askableActorRef.ask(UntypedIdAndCommand(id, cmd))(askTimeout).mapTo[Events]
  }

  def state(): Future[A] = {

    import scala.concurrent.ExecutionContext.Implicits.global

    val eventualState = askableActorRef.ask(GetState(id))(askTimeout)

    eventualState.flatMap { res =>
      // can't use mapTo since we don't have a ClassTag for Aggregate in scope
      val tryCast = Try(res.asInstanceOf[Aggregate])
      Future.fromTry(tryCast)
    }
  }

  def isInitialized: Future[Boolean] = {
    askableActorRef.ask(Exists(id))(askTimeout).mapTo[Boolean]
  }

  def join(viewName: String): ViewBoundedAggregateActorRef[A, C, E, I] = {
    new ViewBoundedAggregateActorRef[A, C, E, I](this, viewName, projectionMonitor)
  }

  def exists(predicate: (Aggregate) => Boolean): Future[Boolean] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    isInitialized.flatMap { initialized =>
      if (initialized)
        state().map(predicate)
      else
        Future.successful(false)
    }
  }
}

class ViewBoundedAggregateActorRef[A, C, E, I <: AggregateId](
    aggregateRef: AggregateActorRef[A, C, E, I],
    defaultView: String,
    projectionMonitorActorRef: ActorRef,
    eventsFilter: EventsFilter = All
) extends AggregateAliases {

  type Aggregate           = A
  type Id                  = I
  type Command             = C
  type Event               = E
  type EventsWithCommandId = immutable.Seq[EventWithCommandId]

  val underlyingRef = aggregateRef

  // Delegation to underlying AsyncAggregateService
  def state()(implicit timeout: Timeout, sender: ActorRef): Future[A]        = underlyingRef.state()
  def isInitialized(implicit timeout: Timeout, sender: ActorRef): Future[Boolean] = underlyingRef.isInitialized
  def exists()(implicit timeout: Timeout, sender: ActorRef): Future[Boolean] = isInitialized

  def withFilter(eventsFilter: EventsFilter): ViewBoundedAggregateActorRef[A, C, E, I] =
    new ViewBoundedAggregateActorRef(underlyingRef, defaultView, projectionMonitorActorRef, eventsFilter)

  def limit(count: Int): ViewBoundedAggregateActorRef[A, C, E, I] = withFilter(Limit(count))

  def ?(cmd: Command with CommandIdFacet)(implicit timeout: Timeout, sender: ActorRef = Actor.noSender): Future[Events] = ask(cmd)

  def ask(cmd: Command with CommandIdFacet)(implicit timeout: Timeout, sender: ActorRef = Actor.noSender): Future[Events] = {
    watchEvents(cmd) {
      underlyingRef ? cmd
    }
  }

  /**
    * Watch for Events originated from the passed Command until they are applied to the ReadModel.
    *
    * @param cmd - a Command to be sent
    * @param sendCommandFunc - a function that will send the `cmd` to the Write Model.
    * @param timeout - an implicit (or explicit) [[Timeout]] after which this call will return a failed Future
    * @return - A Future with a [[Events]]. Future will complete succeffully iff the events originated from `cmd`
    *         are effectively applied on the Read Model, otherwise a [[scala.util.Failure]] holding a [[ProjectionJoinException]]
    *        is returned containing the Events and the Exception indicating the cause of the failure on the Read Model.
    *         Returns a failed Future if `Command` is not valid in which case no Events are generated.
    */
  private def watchEvents(cmd: Command with CommandIdFacet)(sendCommandFunc: => Future[Any])(implicit timeout: Timeout): Future[Events] = {

    // need it explicitly because akka.pattern.ask conflicts with AggregateRef.ask
    val askableProjectionMonitorActorRef = akkaAsk(projectionMonitorActorRef)

    import scala.concurrent.ExecutionContext.Implicits.global
    def newEventsMonitor() = {
      (askableProjectionMonitorActorRef ? ProjectionMonitorActor.EventsMonitorRequest(cmd.id, defaultView)).mapTo[ActorRef]
    }

    val resultOnWrite =
      for {
        // initialize an EventMonitor for the given command
        monitor <- newEventsMonitor()
        // send command to Write Model (AggregateManager)
        events <- sendCommandFunc.mapTo[EventsWithCommandId]

        // need it explicitly because akka.pattern.ask conflicts with AggregatRef.ask
      } yield (akkaAsk(monitor), events)

    val resultOnRead =
      for {
        (monitor, events) <- resultOnWrite
        // apply filter and define which events we want to watch
        toWatch = eventsFilter.filter(events)
        // subscribe for events on the Read Model
        result <- (monitor ? Subscribe(toWatch)).mapTo[EventsMonitorActor.Done.type]
      } yield toWatch

    resultOnRead
      .mapTo[Events]
      .recoverWith {
        // on failure, we send the events we got from the Write Model
        // together with the exception that made it fail (probably a timeout)
        case NonFatal(e) =>
          resultOnWrite.flatMap {
            case (_, evts) => Future.failed(new ProjectionJoinException(evts, defaultView, e))
          }
      }
  }

}

class ProjectionJoinException(val evts: Seq[Any], val viewName: String, val cause: Throwable)
    extends RuntimeException(s"Failed to join projection '$viewName' for events $evts", cause)

trait EventsFilter {
  def filter[E](events: immutable.Seq[E]): immutable.Seq[E]
}

case object All extends EventsFilter {
  def filter[E](events: immutable.Seq[E]): immutable.Seq[E] = events
}

case class Limit(count: Int) extends EventsFilter {

  def filter[E](events: immutable.Seq[E]): immutable.Seq[E] = {
    events.take(count)
  }
}
