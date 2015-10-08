package io.strongtyped.funcqrs

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

  type Id <: AggregateID

  def aggregateId: Id
  def commandId: CommandId
  def eventId: EventId
  def date: OffsetDateTime
  def tags: Set[Tag]
}

/**
 * Enriches [[DomainEvent]] with [[Metadata]] information.
 * @tparam M a Metadata subtype
 */
trait MetadataFacet[M <: Metadata] {
  this: DomainEvent =>

  def metadata: M

  final def id: EventId = metadata.eventId
  final def aggregateId: M#Id = metadata.aggregateId
  final def commandId: CommandId = metadata.commandId
  final def date: OffsetDateTime = metadata.date
  final def tags: Set[Tag] = metadata.tags
}

