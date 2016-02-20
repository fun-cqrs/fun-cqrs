package lottery.domain.service

import akka.NotUsed
import akka.actor.ActorContext
import akka.persistence.query.journal.leveldb.scaladsl.LeveldbReadJournal
import akka.persistence.query.{ EventEnvelope, PersistenceQuery }
import akka.stream.scaladsl.Source
import io.funcqrs.Tag
import io.funcqrs.akka.EventsSourceProvider

// tag::leveldb-events-source[]
class LevelDbTaggedEventsSource(tag: Tag) extends EventsSourceProvider {

  /**
   * Builds a [[Source]] of [[EventEnvelope]]s containing the [[Tag]]
   * and starting from the passed offset.
   *
   * @param offset - initial offset to start to read from
   * @return
   */
  def source(offset: Long)(implicit context: ActorContext): Source[EventEnvelope, NotUsed] = {

    val readJournal =
      PersistenceQuery(context.system)
        .readJournalFor[LeveldbReadJournal](LeveldbReadJournal.Identifier)

    readJournal.eventsByTag(tag.value, offset)
  }

}
// end::leveldb-events-source[]
