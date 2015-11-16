package lottery.app

import akka.actor.Actor
import akka.persistence.query.journal.leveldb.scaladsl.LeveldbReadJournal
import akka.persistence.query.{ EventEnvelope, PersistenceQuery }
import akka.stream.scaladsl.Source
import io.strongtyped.funcqrs.Tag
import io.strongtyped.funcqrs.akka.EventsSourceProvider

trait LevelDbTaggedEventsSource extends EventsSourceProvider {
  this: Actor =>

  /** The [[Tag]] to query events. Only events tagged with this [[Tag]] will be returned.
    * @return
    */
  def tag: Tag

  /** Builds a [[Source]] of [[EventEnvelope]]s containing the [[Tag]] and starting from the passed offset.
    *
    * @param offset - initial offset to start read from
    * @return
    */
  def source(offset: Long): Source[EventEnvelope, Unit] = {

    val readJournal =
      PersistenceQuery(context.system)
        .readJournalFor[LeveldbReadJournal](LeveldbReadJournal.Identifier)

    readJournal.eventsByTag(tag.value, offset)
  }

}
