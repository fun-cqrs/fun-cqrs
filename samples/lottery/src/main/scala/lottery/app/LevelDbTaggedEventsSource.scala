package lottery.app

import akka.actor.ActorContext
import akka.persistence.query.journal.leveldb.scaladsl.LeveldbReadJournal
import akka.persistence.query.PersistenceQuery
import akka.stream.scaladsl.Source
import io.funcqrs.Tag
import io.funcqrs.akka.EventsSourceProvider
import io.funcqrs.backend.EventEnvelope

class LevelDbTaggedEventsSource(tag: Tag) extends EventsSourceProvider {

  /**
   * Builds a [[Source]] of [[EventEnvelope]]s containing the [[Tag]] and starting from the passed offset.
   *
   * @param offset - initial offset to start read from
   * @return
   */
  def source(offset: Long)(implicit context: ActorContext): Source[EventEnvelope, Unit] = {

    val readJournal =
      PersistenceQuery(context.system)
        .readJournalFor[LeveldbReadJournal](LeveldbReadJournal.Identifier)

    readJournal.eventsByTag(tag.value, offset).map { ev =>
      EventEnvelope(ev.offset, ev.sequenceNr, ev.event)
    }
  }

}
