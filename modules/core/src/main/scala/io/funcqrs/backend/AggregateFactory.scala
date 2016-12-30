package io.funcqrs.backend

import io.funcqrs.behavior.api.Types
import io.funcqrs.{ AggregateId, AggregateRef }

import scala.language.higherKinds
import scala.reflect.ClassTag

trait AggregateFactory[F[_]] {

  type Ref[A] = AggregateRef[A, F]

  def aggregateRef[A](implicit types: Types[A], tag: ClassTag[A]) = new WrapperHelper[A]

  protected class WrapperHelper[A](implicit types: Types[A], tag: ClassTag[A]) {
    def forId(id: types.Id): AggregateRef[A, F] = aggregateRefById(id)
  }

  protected def aggregateRefById[A, I <: AggregateId](id: I)(implicit types: Types[A], tag: ClassTag[A]): Ref[A]
}
