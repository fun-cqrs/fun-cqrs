package io.funcqrs.akka

import _root_.akka.actor.{ ActorRef, ActorSystem, Props }
import _root_.akka.pattern._
import _root_.akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import io.funcqrs._
import io.funcqrs.akka.ProjectionActor.FailureStrategy
import io.funcqrs.behavior.Behavior
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.implicitConversions

class FunCQRS(val actorSystem: ActorSystem) extends LazyLogging {

  /** Parent actor for all projections!
    */
  private val projectionMonitorActorRef = actorSystem.actorOf(Props(classOf[ProjectionMonitorActor]), "projectionMonitor")

}

object FunCQRS {

  val api = Api

  object Api {

    def config(config: ProjectionConfig)(implicit funCQRS: FunCQRS): Future[Unit] = {

      // Timeout for the actor creation response. Certainly exaggerated!!
      implicit val actorCreationTimeout = Timeout(3.seconds)

      def configFromProps(projectionProps: ProjectionProps) =
        projection(projectionProps.props, projectionProps.name)

      def projection(props: Props, name: String) = {
        import scala.concurrent.ExecutionContext.Implicits.global
        (funCQRS.projectionMonitorActorRef ? ProjectionMonitorActor.CreateProjection(props, name)).map(_ => ())
      }

      config match {
        case projectionProps: ProjectionProps => configFromProps(projectionProps)
        case factory: ProjectionPropsFactory  => configFromProps(factory.toProps)
      }

    }

    def actorOf[A <: AggregateLike](config: AggregateConfig[A])(implicit funCQRS: FunCQRS): ActorRef = {
      config.name match {
        case Some(name) =>
          funCQRS.actorSystem.actorOf(ConfigurableAggregateManager.props(config.behavior, config.idStrategy), name)
        case None =>
          // let Akka pick a unique name
          funCQRS.actorSystem.actorOf(ConfigurableAggregateManager.props(config.behavior, config.idStrategy))
      }
    }

    def config[A <: AggregateLike](config: AggregateConfigWithAssignedId[A])(implicit funCQRS: FunCQRS): AggregateServiceWithAssignedId[A] = {
      new AggregateServiceWithAssignedId[A] {
        val aggregateManager = actorOf[A](config)

        def projectionMonitorActorRef: ActorRef = funCQRS.projectionMonitorActorRef
      }
    }

    def config[A <: AggregateLike](config: AggregateConfigWithManagedId[A])(implicit funCQRS: FunCQRS): AggregateServiceWithManagedId[A] = {
      new AggregateServiceWithManagedId[A] {
        val aggregateManager = actorOf[A](config)

        def projectionMonitorActorRef: ActorRef = funCQRS.projectionMonitorActorRef
      }
    }

    def aggregate[A <: AggregateLike](behavior: A#Id => Behavior[A]): AggregateConfigWithAssignedId[A] = {
      AggregateConfigWithAssignedId[A](None, behavior, AssignedIdStrategy[A])
    }

    def projection(sourceProvider: EventsSourceProvider,
                   projection: Projection,
                   name: String): ProjectionPropsFactory = {
      ProjectionPropsFactory(sourceProvider, projection, name)
    }

    // ================================================================================
    // support classes and trait for AggregateService creation!
    trait Config

    trait AggregateConfig[A <: AggregateLike] extends Config {

      def name: Option[String]

      def behavior: (A#Id) => Behavior[A]

      def idStrategy: AggregateIdStrategy[A]

      def withName(name: String): AggregateConfig[A]

      /** Configure Aggregate to use an [[AssignedIdStrategy]].
        *
        * Aggregate Ids are defined externally.
        */
      def withAssignedId: AggregateConfigWithAssignedId[A] = {
        AggregateConfigWithAssignedId(name, behavior, AssignedIdStrategy[A])
      }

      /** Configure Aggregate to use an [[GeneratedIdStrategy]].
        * On each create command, a new unique Id will be generated.
        *
        * @param gen - a by-name parameter that should, whenever evaluated, return a unique Aggregate Id
        */
      def withGeneratedId(gen: => A#Id): AggregateConfigWithManagedId[A] = {
        val strategy = new GeneratedIdStrategy[A] {
          def generateId(): Id = gen
        }
        AggregateConfigWithManagedId(name, behavior, strategy)
      }

      /** Configure Aggregate to use a fixed Id.
        *
        * A [[SingletonIdStrategy]] will be constructed using the passed Id
        * @param uniqueId - the fixed Id to be used for this Aggregate
        */
      def withSingletonId(uniqueId: A#Id): AggregateConfigWithManagedId[A] = {
        val strategy = new SingletonIdStrategy[A] {
          val id: Id = uniqueId
        }
        AggregateConfigWithManagedId(name, behavior, strategy)
      }

    }

    case class AggregateConfigWithAssignedId[A <: AggregateLike](name: Option[String],
                                                                 behavior: (A#Id) => Behavior[A],
                                                                 idStrategy: AggregateIdStrategy[A]) extends AggregateConfig[A] {

      def withName(name: String): AggregateConfigWithAssignedId[A] =
        this.copy(name = Option(name))

    }

    case class AggregateConfigWithManagedId[A <: AggregateLike](name: Option[String],
                                                                behavior: (A#Id) => Behavior[A],
                                                                idStrategy: AggregateIdStrategy[A]) extends AggregateConfig[A] {

      def withName(name: String): AggregateConfigWithManagedId[A] =
        this.copy(name = Option(name))

    }

    trait IdStrategy

    trait AssignedId extends IdStrategy

    trait ManagedId extends IdStrategy

    // ================================================================================

    // ================================================================================
    // support classes and trait for Projection creation!
    trait OffsetPersistenceStrategy

    case object NoOffsetPersistenceStrategy extends OffsetPersistenceStrategy

    case class OffsetManagedByAkkaPersistenceStrategy(persistenceId: String) extends OffsetPersistenceStrategy

    trait CustomOffsetPersistenceStrategy extends OffsetPersistenceStrategy {

      def saveCurrentOffset(offset: Long): Future[Unit]

      /** Returns the current offset as persisted in DB */
      def readOffset: Future[Long]

    }

    trait ProjectionConfig

    case class ProjectionProps(props: Props, name: String) extends ProjectionConfig

    case class ProjectionPropsFactory(sourceProvider: EventsSourceProvider,
                                      projection: Projection,
                                      name: String,
                                      failureStrategy: FailureStrategy = PartialFunction.empty,
                                      offsetPersistenceStrategy: OffsetPersistenceStrategy = NoOffsetPersistenceStrategy)
        extends ProjectionConfig {

      def withoutOffsetPersistence: ProjectionPropsFactory = {
        this.copy(offsetPersistenceStrategy = NoOffsetPersistenceStrategy)
      }

      def withOffsetManagedByAkkaPersistence: ProjectionPropsFactory = {
        this.copy(offsetPersistenceStrategy = OffsetManagedByAkkaPersistenceStrategy(name))
      }

      def withCustomOffsetPersistence(strategy: CustomOffsetPersistenceStrategy): ProjectionPropsFactory = {
        this.copy(offsetPersistenceStrategy = strategy)
      }

      /** Defines the  [[FailureStrategy]]
        * @param failureStrategy - a partial function that should handle failures
        * @return
        */
      def onFailure(failureStrategy: FailureStrategy) = {
        this.copy(failureStrategy = failureStrategy)
      }

      def toProps = {
        // which strategy??
        val props =
          offsetPersistenceStrategy match {

            case NoOffsetPersistenceStrategy =>
              ProjectionActorWithoutOffsetPersistence.props(projection, sourceProvider, failureStrategy)

            case OffsetManagedByAkkaPersistenceStrategy(persistenceId) =>
              ProjectionActorWithOffsetManagedByAkkaPersistence.props(projection, sourceProvider, failureStrategy, persistenceId)

            case strategy: CustomOffsetPersistenceStrategy =>
              ProjectionActorWithCustomOffsetPersistence.props(
                projection,
                sourceProvider,
                failureStrategy,
                strategy
              )
          }
        ProjectionProps(props, name)
      }
    }

    // ================================================================================
  }
}
