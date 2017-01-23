package io.funcqrs.projections

case class Envelope(event: Any, offset: Long)
