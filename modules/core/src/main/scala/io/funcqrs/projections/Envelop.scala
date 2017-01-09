package io.funcqrs.projections

case class Envelop[E](event: E, offset: Long)
