package io.funcqrs.projections

import org.reactivestreams.Publisher

trait PublisherFactory[O, E] {
  def from(offset: Option[O]): Publisher[(O, E)]
}
