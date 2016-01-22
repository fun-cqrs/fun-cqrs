package io.funcqrs.backend.akka

import _root_.akka.pattern._
import _root_.akka.actor.ActorRef
import _root_.akka.util.Timeout
import io.funcqrs.akka.AggregateManager.{ Exists, GetState }
import io.funcqrs.akka.EventsMonitorActor.Subscribe
import io.funcqrs.akka.{ EventsMonitorActor, ProjectionMonitorActor }
import io.funcqrs._

import scala.collection.immutable
import scala.concurrent.Future
import scala.util.Try
import scala.util.control.NonFatal

class AggregateServiceAkka[A <: AggregateLike](
    aggregateManager: ActorRef,
    projectionMonitorActorRef: ActorRef
)(implicit askTimeout: Timeout) extends AggregateAliases { service =>

  type Aggregate = A

  def state(id: Id)(implicit askTimeout: Timeout): Future[A] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    (aggregateManager ? GetState(id)).flatMap { res =>
      // can't use mapTo since we don't have a ClassTag for Aggregate in scope
      val tryCast = Try(res.asInstanceOf[Aggregate])
      Future.fromTry(tryCast)
    }
  }

  def exists(id: Id)(implicit askTimeout: Timeout): Future[Boolean] = {
    (aggregateManager ? Exists(id)).mapTo[Boolean]
  }

  def update(id: Id)(cmd: Command)(implicit askTimeout: Timeout): Future[Events] = {
    (aggregateManager ? (id, cmd)).mapTo[Events]
  }

  def newInstance(cmd: Command)(implicit askTimeout: Timeout): Future[Events] = {
    (aggregateManager ? cmd).mapTo[Events]
  }
  def newInstance(id: Id, cmd: Command)(implicit askTimeout: Timeout): Future[Events] = {
    (aggregateManager ? (id, cmd)).mapTo[Events]
  }

  def join(viewName: String): ViewBoundedAggregateService[A] = {
    new ViewBoundedAggregateService[A](service, viewName, projectionMonitorActorRef)
  }
}

class ViewBoundedAggregateService[A <: AggregateLike](
    asyncAggregateService: AggregateServiceAkka[A],
    defaultView: String,
    projectionMonitorActorRef: ActorRef,
    eventsFilter: EventsFilter = All
) extends AggregateAliases {

  type Aggregate = A

  val underlyingService = asyncAggregateService

  // Delegation to underlying AsyncAggregateService
  def state(id: Id)(implicit askTimeout: Timeout): Future[Aggregate] = underlyingService.state(id)
  def exists(id: Id)(implicit askTimeout: Timeout): Future[Boolean] = underlyingService.exists(id)

  def withFilter(eventsFilter: EventsFilter): ViewBoundedAggregateService[A] =
    new ViewBoundedAggregateService(asyncAggregateService, defaultView, projectionMonitorActorRef, eventsFilter)

  def limit(count: Int): ViewBoundedAggregateService[A] = withFilter(Limit(count))

  def update(id: Id)(cmd: Command)(implicit timeout: Timeout): Future[Events] = {
    watchEvents(cmd) {
      asyncAggregateService.update(id)(cmd)
    }
  }

  def newInstance(id: Id, cmd: Command)(implicit timeout: Timeout): Future[Events] = {
    watchEvents(cmd) {
      asyncAggregateService.newInstance(id, cmd)
    }
  }

  def newInstance(cmd: Command)(implicit timeout: Timeout): Future[Events] = {
    watchEvents(cmd) {
      asyncAggregateService.newInstance(cmd)
    }
  }

  /**
   * Watch for [[DomainEvent]]s originated from the passed [[DomainCommand]] until they are applied to the ReadModel.
   *
   * @param cmd - a [[DomainCommand]] to be sent
   * @param sendCommandFunc - a function that will send the `cmd` to the Write Model.
   * @param timeout - an implicit (or explicit) [[Timeout]] after which this call will return a failed Future
   *
   * @return - A Future with a [[Events]]. Future will complete succeffully iff the events originated from `cmd`
   *         are effectively applied on the Read Model, otherwise a [[scala.util.Failure]] holding a [[ProjectionJoinException]]
   *        is returned containing the Events and the Exception indicating the cause of the failure on the Read Model.
   *         Returns a failed Future if `Command` is not valid in which case no Events are generated.
   */
  private def watchEvents(cmd: Command)(sendCommandFunc: => Future[Any])(implicit timeout: Timeout): Future[Events] = {

    import scala.concurrent.ExecutionContext.Implicits.global
    def newEventsMonitor() = {
      (projectionMonitorActorRef ? ProjectionMonitorActor.EventsMonitorRequest(cmd.id, defaultView)).mapTo[ActorRef]
    }

    val resultOnWrite =
      for {
        // initialize an EventMonitor for the given command
        monitor <- newEventsMonitor()
        // send command to Write Model (AggregateManager)
        events <- sendCommandFunc.mapTo[Events]
      } yield (monitor, events)

    val resultOnRead =
      for {
        (monitor, events) <- resultOnWrite
        // apply filter and define which events we want to watch
        toWatch = eventsFilter.filter(events)
        // subscribe for events on the Read Model
        result <- (monitor ? Subscribe(toWatch)).mapTo[EventsMonitorActor.Done.type]
      } yield toWatch

    resultOnRead.recoverWith {
      // on failure, we send the events we got from the Write Model
      // together with the exception that made it fail (probably a timeout)
      case NonFatal(e) => resultOnWrite.flatMap {
        case (_, evts) => Future.failed(new ProjectionJoinException(evts, e))
      }
    }
  }

  class ProjectionJoinException(evts: Events, cause: Throwable) extends RuntimeException
}

trait EventsFilter {
  def filter[E <: DomainEvent](events: immutable.Seq[E]): immutable.Seq[E]
}

case object All extends EventsFilter {
  def filter[E <: DomainEvent](events: immutable.Seq[E]): immutable.Seq[E] = events
}

case class Limit(count: Int) extends EventsFilter {

  def filter[E <: DomainEvent](events: immutable.Seq[E]): immutable.Seq[E] = {
    events.take(count)
  }
}
