package fun.cqrs

import java.time.OffsetDateTime


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

