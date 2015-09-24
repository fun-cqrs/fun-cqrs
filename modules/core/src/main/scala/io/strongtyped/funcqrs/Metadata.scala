package io.strongtyped.funcqrs

import java.time.OffsetDateTime


/** Holds DomainEvent metadata information such as:
  * - aggregateId
  * - CommandId
  * - EventId
  * - event date
  * - tags
  */
trait Metadata {

  type Identifier <: AggregateIdentifier

  def aggregateId: Identifier
  def commandId: CommandId
  def eventId: EventId
  def date: OffsetDateTime
  def tags: Set[Tag]
}


trait MetadataFacet[M <: Metadata] {
  this: DomainEvent =>

  def metadata: M

  final def id: EventId = metadata.eventId
  final def aggregateId: M#Identifier = metadata.aggregateId
  final def commandId: CommandId = metadata.commandId
  final def date: OffsetDateTime = metadata.date
  final def tags: Set[Tag] = metadata.tags
}

