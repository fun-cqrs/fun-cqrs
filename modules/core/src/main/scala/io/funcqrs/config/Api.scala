package io.funcqrs.config

import io.funcqrs.backend.Query
import io.funcqrs.behavior.Behavior
import io.funcqrs.{ AggregateLike, Projection }

import scala.language.higherKinds

object Api {

  /** Initiates the configuration of an Aggregate */
  def aggregate[A <: AggregateLike](behavior: A#Id => Behavior[A]): AggregateConfig[A] = {
    AggregateConfig[A](None, behavior)
  }

  /** Initiates the configuration of a Projection */
  def projection(query: Query, projection: Projection, name: String): ProjectionConfig = {
    ProjectionConfig(query, projection, name)
  }

}
