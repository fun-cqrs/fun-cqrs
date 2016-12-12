package io.funcqrs.config

import io.funcqrs.behavior.api.Behavior

case class AggregateConfig[A, C, E, I](
    name: Option[String],
    behavior: (I) => Behavior[A, C, E]
) {

  def withName(name: String): AggregateConfig[A, C, E, I] =
    this.copy(name = Option(name))

}
