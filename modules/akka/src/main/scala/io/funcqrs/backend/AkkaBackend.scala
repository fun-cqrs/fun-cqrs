package io.funcqrs.backend
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.pattern._
import akka.util.Timeout
import io.funcqrs.akka.AggregateManager.{Exists, GetState}
import io.funcqrs.AggregateService
import io.funcqrs.akka._
import io.funcqrs.backend.Api._
import io.funcqrs.{AggregateLike, AggregateServiceWithAssignedId, AggregateServiceWithManagedId}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

class AkkaBackend(val actorSystem: ActorSystem, askTimeout: Timeout) extends Backend {

  type F[_] = Future[_]

  /**
   * Parent actor for all projections!
   */
  private val projectionMonitorActorRef = actorSystem.actorOf(Props(classOf[ProjectionMonitorActor]), "projectionMonitor")

  def configureProjection(config: ProjectionConfig): Unit = {

    // for Akka we need a Akka-Streams Source
    // we can build one from a ReactiveStream Publisher
    val sourceProvider: EventsSourceProvider = EventsSourceProvider.fromPublisher(config.publisherProvider)

    // Timeout for the actor creation response. Certainly exaggerated!!
    implicit val actorCreationTimeout = Timeout(3.seconds)

    def projectionProps = {
      // which strategy??
      // build different ProjectionActor depending on the chosen Offset Persistence Strategy
      config.offsetPersistenceStrategy match {

        case NoOffsetPersistenceStrategy =>
          ProjectionActorWithoutOffsetPersistence.props(config.projection, sourceProvider)

        case BackendOffsetPersistenceStrategy(persistenceId) =>
          ProjectionActorWithOffsetManagedByAkkaPersistence.props(config.projection, sourceProvider, persistenceId)

        case strategy: CustomOffsetPersistenceStrategy =>
          ProjectionActorWithCustomOffsetPersistence.props(config.projection, sourceProvider, strategy)
      }
    }

    projectionMonitorActorRef ? ProjectionMonitorActor.CreateProjection(projectionProps, config.name)

    () // return Unit
  }

  trait AggregateServiceAkka[A <: AggregateLike] extends AggregateService[A, F] {
    def aggregateManager: ActorRef

    // ask timeout should only be in scope inside service
    implicit val timeout = askTimeout
    def state(id: Id): Future[A] = {
      import scala.concurrent.ExecutionContext.Implicits.global
      (aggregateManager ? GetState(id)).flatMap { res =>
        // can't use mapTo since we don't have a ClassTag for Aggregate in scope
        val tryCast = Try(res.asInstanceOf[Aggregate])
        Future.fromTry(tryCast)
      }
    }

    def exists(id: Id): Future[Boolean] = {
      (aggregateManager ? Exists(id)).mapTo[Boolean]
    }

    def update(id: Id)(cmd: Command): Future[Events] = {
      (aggregateManager ? (id, cmd)).mapTo[Events]
    }
  }

  def configureAggregate[A <: AggregateLike](config: AggregateConfigWithAssignedId[A]): AggregateServiceWithAssignedId[A, F] = {

    new AggregateServiceWithAssignedId[A, F] with AggregateServiceAkka[A] {
      val aggregateManager = actorOf[A](config)

      def newInstance(id: Id, cmd: Command): Future[Events] = {
        (aggregateManager ? (id, cmd)).mapTo[Events]
      }
    }
  }

  def configureAggregate[A <: AggregateLike](config: AggregateConfigWithManagedId[A]): AggregateServiceWithManagedId[A, F] = {

    new AggregateServiceWithManagedId[A, F] with AggregateServiceAkka[A]{

      val aggregateManager = actorOf[A](config)

      def newInstance(cmd: Command): Future[Events] = {
        (aggregateManager ? cmd).mapTo[Events]
      }
    }
  }

  private def actorOf[A <: AggregateLike](config: AggregateConfig[A]): ActorRef = {
    config.name match {
      case Some(name) =>
        actorSystem.actorOf(ConfigurableAggregateManager.props(config.behavior, config.idStrategy), name)
      case None =>
        // let Akka pick a unique name
        actorSystem.actorOf(ConfigurableAggregateManager.props(config.behavior, config.idStrategy))
    }
  }
}