package io.funcqrs.backend

import java.util.NoSuchElementException

import io.funcqrs._
import io.funcqrs.config.{ AggregateConfig, ProjectionConfig }

import scala.language.higherKinds
import scala.reflect.ClassTag

trait Backend[F[_]] extends AggregateFactory[F] {

  /** Configure a Aggregate */
  def configure[A: ClassTag, C, E, I <: AggregateId](config: AggregateConfig[A, C, E, I]): Backend[F]

  def configure(config: ProjectionConfig): Backend[F]

  protected def configLookup[A: ClassTag, C, E, I](lookup: => AggregateConfig[A, C, E, I]) = {
    try {
      lookup
    } catch {
      case _: NoSuchElementException =>
        val cls = ClassTagImplicits[A]
        throw new MissingAggregateConfigurationException(s"No configuration found for ${cls.runtimeClass.getSimpleName}")
    }
  }
}
