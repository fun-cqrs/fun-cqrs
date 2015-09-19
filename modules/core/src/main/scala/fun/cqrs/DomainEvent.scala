package fun.cqrs

import java.time.OffsetDateTime
import java.util.UUID


trait DomainEvent {

  def metadata: Metadata
}


case class EventId(value: UUID = UUID.randomUUID())


/** Holds DomainEvent metadata information such as:
  * - aggregateId
  * - command id
  * - event id
  * - event date
  */
case class Metadata(aggregateId: AggregateIdentifier,
                    eventId: EventId,
                    date: OffsetDateTime,
                    tags: Set[Tag]) {

  def withTags(tags: Tag*): Metadata = {
    val tagSet = tags.toSet
    this.copy(tags = this.tags ++ tagSet)
  }
}


object Metadata {

  def metadata(tags: Tag*): AggregateIdentifier => Metadata = { aggregateId =>
    Metadata(aggregateId, EventId(), OffsetDateTime.now(), tags.toSet)
  }
}


case class Tag(key: String, value: String)

object Tags {

  def aggregateTag(value: String) = Tag("aggregateType", value)

  def dependentViews(value: String) = Tag("dependentViews", value)
}



