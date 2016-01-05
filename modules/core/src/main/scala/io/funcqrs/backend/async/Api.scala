package io.funcqrs.backend.async

import io.funcqrs.behavior.Behavior
import io.funcqrs._
import io.funcqrs.backend._

import scala.concurrent.Future

object Api {

  def config(projectionConfig: ProjectionConfig)(implicit backend: Backend): Unit = {
    backend.configureProjection(projectionConfig)
  }

  def config[A <: AggregateLike](aggregateConfig: AggregateConfigWithAssignedId[A])(implicit backend: Backend): AggregateServiceWithAssignedId[A, Future] = {
    backend.configureAggregate(aggregateConfig).asInstanceOf[AggregateServiceWithAssignedId[A, Future]]
  }

  def config[A <: AggregateLike](aggregateConfig: AggregateConfigWithManagedId[A])(implicit backend: Backend): AggregateServiceWithManagedId[A, Future] = {
    backend.configureAggregate(aggregateConfig).asInstanceOf[AggregateServiceWithManagedId[A, Future]]
  }

  def aggregate[A <: AggregateLike](behavior: A#Id => Behavior[A]): AggregateConfigWithAssignedId[A] = {
    AggregateConfigWithAssignedId[A](None, behavior, AssignedIdStrategy[A])
  }

  def projection(publisherProvider: EventsPublisherProvider,
                 projection: Projection,
                 name: String): ProjectionConfig = {
    ProjectionConfig(publisherProvider, projection, name)
  }


}