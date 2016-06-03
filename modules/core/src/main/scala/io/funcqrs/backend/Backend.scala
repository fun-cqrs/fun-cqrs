package io.funcqrs.backend

import io.funcqrs.{ AggregateRef, AggregateLike }
import io.funcqrs.config.{ AggregateConfig, ProjectionConfig }

import scala.language.higherKinds
import scala.reflect.ClassTag

trait Backend[F[_]] {

  def configure[A <: AggregateLike: ClassTag](config: AggregateConfig[A]): Backend[F]

  def configure(config: ProjectionConfig): Backend[F]

  def aggregateRef[A <: AggregateLike: ClassTag](id: A#Id): AggregateRef[A, F]
}
