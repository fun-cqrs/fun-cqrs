package io.funcqrs.backend

import io.funcqrs.behavior.Types
import io.funcqrs.{ AggregateId, AggregateRef }

import scala.language.higherKinds
import scala.reflect.ClassTag

trait AggregateFactory[F[_]] {

  type Ref[A, C, E] = AggregateRef[A, C, E, F]

  def aggregateRef[A](implicit types: Types[A], tag: ClassTag[A]) =
    new WrapperHelper[A, types.Command, types.Event, types.Id]

  protected class WrapperHelper[A: ClassTag, C, E, I <: AggregateId] {
    def forId(id: I): AggregateRef[A, C, E, F] = aggregateRefById(id)
  }

  protected def aggregateRefById[A: ClassTag, C, E, I <: AggregateId](id: I): Ref[A, C, E]
}
