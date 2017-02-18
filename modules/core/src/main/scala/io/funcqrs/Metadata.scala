package io.funcqrs

import java.time.OffsetDateTime

/**
  * Holds Metadata information such as:
  * - aggregateId
  * - CommandId
  * - EventId
  * - event date
  * - tags
  *
  * Abstract type Id (subtype of AggregateID) must be defined,
  * as such Metadata's implementation are bounded to specific Aggregate types.
  */
trait Metadata {

  type Id <: AggregateId
  type DateTime

  def aggregateId: Id
  def commandId: CommandId
  def eventId: EventId
  def date: DateTime
  def tags: Set[Tag]
}

trait JavaTime { self: Metadata =>
  type DateTime = OffsetDateTime
}

/**
  * Enriches [[AnyEvent]] with [[Metadata]] information.
  * TODO: we should have a Facet only for tagging
  * @tparam M a Metadata subtype
  */
trait MetadataFacet[M <: Metadata] { this: AnyEvent =>

  def metadata: M

  final def id: EventId          = metadata.eventId
  final def aggregateId: M#Id    = metadata.aggregateId
  final def commandId: CommandId = metadata.commandId
  final def date: M#DateTime     = metadata.date
  final def tags: Set[Tag]       = metadata.tags
}
