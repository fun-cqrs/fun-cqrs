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
                    cmdId: CommandId,
                    eventId: EventId,
                    date: OffsetDateTime,
                    tags: Seq[Tag])


case class Tag(key: String, value: String)


object Metadata {

  def metadata(aggregateId: AggregateIdentifier, cmdId: CommandId, tags: Tag*): Metadata =
    Metadata(aggregateId, cmdId, EventId(), OffsetDateTime.now(), tags)
}

