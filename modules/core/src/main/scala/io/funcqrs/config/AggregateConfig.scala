package io.funcqrs.config

import io.funcqrs._
import io.funcqrs.behavior.Behavior

// ================================================================================
// support classes and traits for AggregateService creation!

case class AggregateConfig[A <: AggregateLike](
    name: Option[String],
    behavior: (A#Id) => Behavior[A]
) {

  def withName(name: String): AggregateConfig[A] =
    this.copy(name = Option(name))

}
