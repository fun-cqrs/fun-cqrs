package io.funcqrs.backend

import io.funcqrs.AggregateRef
import io.funcqrs.behavior.api.Types
import io.funcqrs.config.{ AggregateConfig, ProjectionConfig }

import scala.language.higherKinds

trait Backend[F[_]] {

  /** Configure a Aggregate */
  def configure[A, C, E, I](config: AggregateConfig[A, C, E, I]): Backend[F]

  def configure(config: ProjectionConfig): Backend[F]

  class AggregateRefStage[A, I](implicit types: Types[A]) {
    def apply(id: I): AggregateRef[A, F] = aggregateRefById[A, I](id)
  }

  def aggregateRef[A](implicit types: Types[A]) = new AggregateRefStage[A, types.Id]

  protected def aggregateRefById[A, I](id: I, types: Types[A]): AggregateRef[A, F]
}
