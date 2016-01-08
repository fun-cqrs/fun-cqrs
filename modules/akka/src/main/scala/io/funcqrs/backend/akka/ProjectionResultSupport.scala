package io.funcqrs.backend.akka

import io.funcqrs.{AggregateAliases, AggregateLike}

trait ProjectionResultSupport[A <: AggregateLike] extends AggregateAliases {

  type Aggregate = A

  trait ProjectionResult {
    def events: Events
  }
  case class ProjectionSuccess(events: Events) extends ProjectionResult
  case class ProjectionFailure(events: Events, throwable: Throwable) extends ProjectionResult

}
