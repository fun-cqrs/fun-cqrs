package io.strongtyped.funcqrs

trait MetadataFacet {
  this: DomainEvent =>

  def metadata: Metadata

  final def id: EventId = metadata.eventId
  final def aggregateId: AggregateIdentifier = metadata.aggregateId
}
