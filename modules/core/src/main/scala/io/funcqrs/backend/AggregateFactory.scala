package io.funcqrs.backend

import io.funcqrs.behavior.api.Types
import io.funcqrs.{ AggregateId, AggregateRef }

import scala.language.higherKinds
import scala.reflect.ClassTag

trait AggregateFactory[F[_]] {

  type Ref[A, C, E] = AggregateRef[A, C, E, F]

  def aggregateRef[A](implicit types: Types[A], tag: ClassTag[A]) =
    new WrapperHelper[A, types.Command, types.Event, types.Id](types)

  protected class WrapperHelper[A: ClassTag, C, E, I <: AggregateId](types: Types.Aux[A, I]) {
    def forId(id: I): AggregateRef[A, C, E, F] = aggregateRefById(id)(types)
  }

  protected def aggregateRefById[A, C, E, I <: AggregateId](id: I)(types: Types.Aux[A, I])(implicit tag: ClassTag[A]): Ref[A, C, E]
}
