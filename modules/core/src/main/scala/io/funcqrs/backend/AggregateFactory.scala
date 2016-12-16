package io.funcqrs.backend

import io.funcqrs.{ AggregateId, AggregateRef }
import io.funcqrs.behavior.api.Types

import scala.language.higherKinds
import scala.reflect.ClassTag

trait AggregateFactory[F[_]] {

  type Ref[A] = AggregateRef[A, F]

  def aggregateRef[A](implicit types: Types[A], tag: ClassTag[A]) = new WrapperHelper[A]

  protected class WrapperHelper[A](implicit types: Types[A], tag: ClassTag[A]) {
    def apply(id: types.Id): AggregateRef[A, F] =
      aggregateRefById(id)
  }

  protected def aggregateRefById[A, I <: AggregateId](id: I)(implicit types: Types.Aux[A, I], tag: ClassTag[A]): Ref[A]
}
