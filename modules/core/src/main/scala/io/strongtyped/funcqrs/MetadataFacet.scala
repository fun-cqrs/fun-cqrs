package io.strongtyped.funcqrs

trait MetadataFacet {
  this: DomainEvent =>

  def metadata: Metadata

  final def aggregateId: AggregateIdentifier = metadata.aggregateId
}
