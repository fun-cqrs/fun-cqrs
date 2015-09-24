package io.strongtyped.funcqrs

import java.time.OffsetDateTime


/** Holds DomainEvent metadata information such as:
  * - aggregateId
  * - command id
  * - event id
  * - event date
  * - tags
  */
case class Metadata(aggregateId: AggregateIdentifier,
                    commandId: CommandId,
                    eventId: EventId,
                    date: OffsetDateTime,
                    tags: Set[Tag]) {

  def withTags(tags: Tag*): Metadata = {
    val tagSet = tags.toSet
    this.copy(tags = this.tags ++ tagSet)
  }
}


object Metadata {

  def metadata(tags: Tag*): (AggregateIdentifier, CommandId) => Metadata = { (aggregateId, cmdId) =>
    Metadata(aggregateId, cmdId, EventId(), OffsetDateTime.now(), tags.toSet)
  }
}

