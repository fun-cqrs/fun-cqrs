package io.funcqrs.config

import io.funcqrs.backend.Query
import io.funcqrs.behavior._
import io.funcqrs.{ AggregateLike, CommandException, Projection }
import scala.language.higherKinds

object Api {

  /** Initiates the configuration of an Aggregate */
  def aggregate[A <: AggregateLike](behaviorFunc: A#Id => Behavior[A]): AggregateConfig[A] = {

    val fallback: Behavior[A] = {
      case anyOtherState =>
        action[A]
          .rejectCommand {
            case anyCommand => new CommandException(s"No actions defined for current state: $anyOtherState")
          }
    }
    // enrich user defined behavior with a fallback
    // this will prevent that MatchError if user fails to define an exhaustive match
    val behaviorWithFallback = (id: A#Id) => {
      behaviorFunc(id) orElse fallback
    }

    AggregateConfig[A](None, behaviorWithFallback)
  }

  /** Initiates the configuration of a Projection */
  def projection(query: Query, projection: Projection, name: String): ProjectionConfig = {
    ProjectionConfig(query, projection, name)
  }

  def projection(query: Query, projection: Projection): ProjectionConfig = {
    ProjectionConfig(query, projection, projection.name)
  }
}
