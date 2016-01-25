package io.funcqrs.backend.akka

import _root_.akka.actor.{ ActorRef, ActorSystem, Props }
import _root_.akka.pattern._
import _root_.akka.util.Timeout
import io.funcqrs._
import io.funcqrs.akka.AggregateManager.{ Exists, GetState }
import io.funcqrs.akka._
import io.funcqrs.behavior.Behavior

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

object AkkaBackendApi {

  class AkkaBackend(val actorSystem: ActorSystem)(implicit askTimeout: Timeout) {

    /** Parent actor for all projections! */
    private val projectionMonitorActorRef = actorSystem.actorOf(Props(classOf[ProjectionMonitorActor]), "projectionMonitor")

    def configure[A <: AggregateLike](config: AggregateConfigWithAssignedId[A]): AggregateService[A] =
      new AggregateService(actorOf[A](config), projectionMonitorActorRef)

    def configure[A <: AggregateLike](config: AggregateConfigWithManagedId[A]): AggregateService[A] =
      new AggregateService(actorOf[A](config), projectionMonitorActorRef)

    def configure(config: ProjectionConfig): Future[Unit] = {

      // which strategy??
      // build different ProjectionActor depending on the chosen Offset Persistence Strategy
      def projectionProps = {
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

  def configure[A <: AggregateLike](aggregateConfig: AggregateConfigWithAssignedId[A])(implicit akkaBackend: AkkaBackend): AggregateService[A] = {
    akkaBackend.configure(aggregateConfig)
  }

  def configure[A <: AggregateLike](aggregateConfig: AggregateConfigWithManagedId[A])(implicit akkaBackend: AkkaBackend): AggregateService[A] = {
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