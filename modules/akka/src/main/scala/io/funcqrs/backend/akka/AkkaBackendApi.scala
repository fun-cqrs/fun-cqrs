package io.funcqrs.backend.akka

import _root_.akka.actor.{ActorRef, ActorSystem, Props}
import _root_.akka.pattern._
import _root_.akka.util.Timeout
import io.funcqrs._
import io.funcqrs.akka.AggregateManager.{Exists, GetState}
import io.funcqrs.akka._
import io.funcqrs.behavior.Behavior

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

object AkkaBackendApi {

  class AkkaBackend(val actorSystem: ActorSystem)(implicit askTimeout: Timeout) {
    /**
     * Parent actor for all projections!
     */
    private val projectionMonitorActorRef = actorSystem.actorOf(Props(classOf[ProjectionMonitorActor]), "projectionMonitor")

    class AggregateServiceAkka[A <: AggregateLike](aggregateManager: ActorRef) extends AsyncAggregateService[A] {

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

      def newInstance(cmd: Command): Future[Events] = {
        (aggregateManager ? cmd).mapTo[Events]
      }
      def newInstance(id: Id, cmd: Command): Future[Events] = {
        (aggregateManager ? (id, cmd)).mapTo[Events]
      }
    }

    def configure[A <: AggregateLike](config: AggregateConfigWithAssignedId[A]): AsyncAggregateService[A] = {
      new AggregateServiceAkka(actorOf[A](config))
    }

    def configure[A <: AggregateLike](config: AggregateConfigWithManagedId[A]): AsyncAggregateService[A] = {
      new AggregateServiceAkka(actorOf[A](config))
    }

    def configure(config: ProjectionConfig): Future[Unit] = {

      def projectionProps = {
        // which strategy??
        // build different ProjectionActor depending on the chosen Offset Persistence Strategy
        config.offsetPersistenceStrategy match {

          case NoOffsetPersistenceStrategy =>
            ProjectionActorWithoutOffsetPersistence.props(config.projection, config.sourceProvider)

          case BackendOffsetPersistenceStrategy(persistenceId) =>
            ProjectionActorWithOffsetManagedByAkkaPersistence.props(config.projection, config.sourceProvider, persistenceId)

          case strategy: CustomOffsetPersistenceStrategy =>
            ProjectionActorWithCustomOffsetPersistence.props(config.projection, config.sourceProvider, strategy)
        }
      }

      // Timeout for the actor creation response. Certainly exaggerated!!
      val actorCreationTimeout = Timeout(3.seconds)

      val created = projectionMonitorActorRef
        .ask(ProjectionMonitorActor.CreateProjection(projectionProps, config.name))(actorCreationTimeout)

      import scala.concurrent.ExecutionContext.Implicits.global
      created.map(_ => Unit)
    }

    def actorOf[A <: AggregateLike](config: AggregateConfig[A]): ActorRef = {
      config.name match {
        case Some(name) =>
          actorSystem.actorOf(ConfigurableAggregateManager.props(config.behavior, config.idStrategy), name)
        case None =>
          // let Akka pick a unique name
          actorSystem.actorOf(ConfigurableAggregateManager.props(config.behavior, config.idStrategy))
      }
    }

  }

  def configure[A <: AggregateLike](aggregateConfig: AggregateConfigWithAssignedId[A])(implicit akkaBackend: AkkaBackend): AsyncAggregateService[A] = {
    akkaBackend.configure(aggregateConfig)
  }

  def configure[A <: AggregateLike](aggregateConfig: AggregateConfigWithManagedId[A])(implicit akkaBackend: AkkaBackend): AsyncAggregateService[A] = {
    akkaBackend.configure(aggregateConfig)
  }

  def configure(config: ProjectionConfig)(implicit akkaBackend: AkkaBackend): Future[Unit] = {
    akkaBackend.configure(config)
  }

  /** Initiates the configuration of an Aggregate */
  def aggregate[A <: AggregateLike](behavior: A#Id => Behavior[A]): AggregateConfigWithAssignedId[A] = {
    AggregateConfigWithAssignedId[A](None, behavior, AssignedIdStrategy[A])
  }

  /** Initiates the configuration of a Projection */
  def projection(sourceProvider: EventsSourceProvider, projection: Projection, name: String): ProjectionConfig = {
    ProjectionConfig(sourceProvider, projection, name)
  }

}