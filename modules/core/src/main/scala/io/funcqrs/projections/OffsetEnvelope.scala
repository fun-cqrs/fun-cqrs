package io.funcqrs.projections

/**
  * An OffsetEnvelope tells you what the offset in the journal was for the Event
  */
case class OffsetEnvelope[O, E](offset: O, event: E)
