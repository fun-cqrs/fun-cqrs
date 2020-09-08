package io.funcqrs.akka.backend

import akka.actor.{ActorPath, ActorRef, ActorSystem, Props}
import akka.pattern._
import akka.util.Timeout
import io.funcqrs.akka._
import io.funcqrs.akka.util.ConfigReader.aggregateConfig
import io.funcqrs.backend._
import io.funcqrs.config._
import io.funcqrs.projections.PublisherFactory
import io.funcqrs.{AggregateId, ClassTagImplicits, MissingAggregateConfigurationException}

import scala.collection.concurrent
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.reflect.ClassTag

trait AkkaBackend extends Backend[Future] {

  val actorSystem: ActorSystem

  /** Parent actor for all projections! */
  lazy private val projectionMonitorActorRef = {
    val className = this.getClass.getSimpleName

    val adjustedName =
      if (ActorPath.isValidPathElement(className)) className
      else "anonymous"

    val name = adjustedName + "-projection-monitor"

    actorSystem.actorOf(Props(classOf[ProjectionMonitorActor]), name)
  }

  private val aggregates: concurrent.Map[ClassTag[_], ActorRef] = concurrent.TrieMap()


  protected def aggregateRefById[A: ClassTag, C, E, I <: AggregateId](id: I): Ref[A, C, E] = {
    val aggregateManager =
      aggregates.getOrElse(
        ClassTagImplicits[A],
        throw new MissingAggregateConfigurationException(
          "The aggregate was not configured with a behaviour. " +
            "Use io.funcqrs.config.Api.aggregate to provide a behaviour for this aggregate."
        )
      )

    val aggregateName = aggregateManager.path.name
    val askTimeout = aggregateConfig(aggregateName).getDuration("ask-timeout", 5.seconds)

    new AggregateActorRef[A, C, E, I](id, aggregateManager, projectionMonitorActorRef, askTimeout)
  }

  def configure[A: ClassTag, C, E, I <: AggregateId](config: AggregateConfig[A, C, E, I]): AkkaBackend = {
    aggregates += (ClassTagImplicits[A] -> actorOf[A, C, E, I](config))
    this
  }

  def configure[O, E](config: ProjectionConfig[O, E]): AkkaBackend = {


    // which strategy??
    // build different ProjectionActor depending on the chosen Offset Persistence Strategy
    def projectionProps = {
      config.offsetPersistenceStrategy match {

        case NoOffsetPersistenceStrategy =>
          ProjectionActorWithoutOffsetPersistence.props[O, E](config.projection, config.publisherFactory)

        case BackendOffsetPersistenceStrategy(persistenceId) =>
          ProjectionActorWithCustomOffsetPersistence.props[Long, E](
            config.projection,
            config.publisherFactory.asInstanceOf[PublisherFactory[Long, E]],
            AkkaOffsetPersistenceStrategy.offsetAsLong(actorSystem, config.name)
          )

        case strategy: CustomOffsetPersistenceStrategy[O] =>
          ProjectionActorWithCustomOffsetPersistence.props[O, E](config.projection, config.publisherFactory, strategy)
      }
    }

    // Timeout for the actor creation response. Certainly exaggerated!!
    val actorCreationTimeout = Timeout(3.seconds)

    val created =
      projectionMonitorActorRef.ask(ProjectionMonitorActor.CreateProjection(projectionProps, config.name))(actorCreationTimeout)

    import scala.concurrent.ExecutionContext.Implicits.global
    created.map(_ => ())

    this
  }

  private def actorOf[A, C, E, I](config: AggregateConfig[A, C, E, I])(implicit ev: ClassTag[A]): ActorRef = {
    val name = config.name.getOrElse(ev.runtimeClass.getSimpleName)
    actorSystem.actorOf(ConfigurableAggregateManager.props(config.behavior), name)
  }

}
