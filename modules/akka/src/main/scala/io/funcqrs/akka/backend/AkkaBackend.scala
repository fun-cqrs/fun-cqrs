package io.funcqrs.akka.backend

import akka.actor.{ ActorRef, ActorSystem, Props }
import akka.pattern._
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.funcqrs.AggregateLike
import io.funcqrs.akka._
import io.funcqrs.backend._
import io.funcqrs.config._
import io.funcqrs.ClassTagImplicits
import io.funcqrs.akka.util.ConfigReader.aggregateConfig

import scala.collection.concurrent
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.reflect.ClassTag

trait AkkaBackend extends Backend[Future] {

  val actorSystem: ActorSystem

  /** Parent actor for all projections! */
  lazy private val projectionMonitorActorRef = actorSystem.actorOf(Props(classOf[ProjectionMonitorActor]), "projectionMonitor")

  private var aggregates: concurrent.Map[ClassTag[_], ActorRef] = concurrent.TrieMap()

  def sourceProvider(query: Query): EventsSourceProvider

  def aggregateRef[A <: AggregateLike: ClassTag](id: A#Id): AggregateActorRef[A] = {
    val aggregateManager = aggregates(ClassTagImplicits[A])

    val aggregateName = aggregateManager.path.name
    val askTimeout = aggregateConfig(aggregateName).getDuration("ask-timeout", 5.seconds)

    new AggregateActorRef[A](id, aggregateManager, projectionMonitorActorRef, askTimeout)
  }

  def configure[A <: AggregateLike: ClassTag](config: AggregateConfig[A]): AkkaBackend = {
    aggregates += (ClassTagImplicits[A] -> actorOf[A](config))
    this
  }

  def configure(config: ProjectionConfig): AkkaBackend = {

    val srcProvider = sourceProvider(config.query)
    // which strategy??
    // build different ProjectionActor depending on the chosen Offset Persistence Strategy
    def projectionProps = {
      config.offsetPersistenceStrategy match {

        case NoOffsetPersistenceStrategy =>
          ProjectionActorWithoutOffsetPersistence.props(config.projection, srcProvider)

        case BackendOffsetPersistenceStrategy(persistenceId) =>
          ProjectionActorWithOffsetManagedByAkkaPersistence.props(config.projection, srcProvider, persistenceId)

        case strategy: CustomOffsetPersistenceStrategy =>
          ProjectionActorWithCustomOffsetPersistence.props(config.projection, srcProvider, strategy)
      }
    }

    // Timeout for the actor creation response. Certainly exaggerated!!
    val actorCreationTimeout = Timeout(3.seconds)

    val created =
      projectionMonitorActorRef.ask(ProjectionMonitorActor.CreateProjection(projectionProps, config.name))(actorCreationTimeout)

    import scala.concurrent.ExecutionContext.Implicits.global
    created.map(_ => Unit)

    this
  }

  def actorOf[A <: AggregateLike](config: AggregateConfig[A])(implicit ev: ClassTag[A]): ActorRef = {
    val name = config.name.getOrElse(ev.runtimeClass.getSimpleName)
    actorSystem.actorOf(ConfigurableAggregateManager.props(config.behavior), name)
  }

}