package fun.cqrs

trait MetadataFacet {
  this: DomainEvent =>

  def metadata: Metadata

  final def aggregateId: AggregateIdentifier = metadata.aggregateId
}
