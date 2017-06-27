package io.funcqrs.config

import io.funcqrs.CommandException
import io.funcqrs.behavior._
import io.funcqrs.projections.{ Projection, PublisherFactory }

import scala.language.higherKinds

object Api {

  /** Initiates the configuration of an Aggregate */
  def aggregate[A, C, E, I](behaviorFunc: I => Behavior[A, C, E]): AggregateConfig[A, C, E, I] = {

    val fallback: Behavior[A, C, E] = {
      case anyOtherState =>
        Actions[A, C, E]()
          .rejectCommand {
            case anyCommand =>
              new CommandException(s"No actions defined for ${anyCommand.getClass.getSimpleName} on current state: $anyOtherState")
          }
    }
    // enrich user defined behavior with a fallback
    // this will prevent MatchErrors if user fails to define an exhaustive match
    val behaviorWithFallback = (id: I) => {
      behaviorFunc(id) orElse fallback
    }

    AggregateConfig[A, C, E, I](None, behaviorWithFallback)
  }

  /** Initiates the configuration of a Projection */
  def projection[O, E](projection: Projection[E], publisherFactory: PublisherFactory[O, E], name: String): ProjectionConfig[O, E] = {
    ProjectionConfig(projection, publisherFactory, name)
  }

  def projection[O, E](projection: Projection[E], publisherFactory: PublisherFactory[O, E]): ProjectionConfig[O, E] = {
    ProjectionConfig(projection, publisherFactory, projection.name)
  }
}
