package raffle.app

import akka.actor.ActorSystem
import akka.persistence.query.journal.leveldb.scaladsl.LeveldbReadJournal
import akka.persistence.query.{PersistenceQuery, Sequence}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import io.funcqrs.akka.backend.AkkaBackend
import io.funcqrs.config.AkkaOffsetPersistenceStrategy
import io.funcqrs.config.Api.{projection, _}
import io.funcqrs.projections.PublisherFactory
import io.funcqrs.test.backend.InMemoryBackend
import org.reactivestreams.Publisher
import raffle.domain.model.{Raffle, RaffleEvent}
import raffle.domain.service.{RaffleViewProjection, RaffleViewRepo}

import scala.language.higherKinds

object AppContext {

  val raffleViewRepo = new RaffleViewRepo

  def akkaBackend(actorSys: ActorSystem) = {

    implicit val system = actorSys

    val backend = new AkkaBackend {
      val actorSystem: ActorSystem = actorSys
    }

    val readJournal =
      PersistenceQuery(actorSys).readJournalFor[LeveldbReadJournal](LeveldbReadJournal.Identifier)

    backend
      .configure {
        // aggregate config - write model
        aggregate(Raffle.behavior)
      }
      .configure {
        projection(
          projection = new RaffleViewProjection(raffleViewRepo),
          publisherFactory = new PublisherFactory[Long, RaffleEvent] {
            override def from(offset: Option[Long]): Publisher[(Long, RaffleEvent)] = {

              val akkaOffset = offset.map(Sequence).getOrElse(Sequence(0))

              readJournal
                .eventsByTag(Raffle.tag.value, akkaOffset)
                .map { akkaEnvelope =>
                  val Sequence(value) = akkaEnvelope.offset
                  (value, akkaEnvelope.event.asInstanceOf[RaffleEvent])
                }
                .runWith(Sink.asPublisher(false))

            }
          }
        ).withCustomOffsetPersistence(
          AkkaOffsetPersistenceStrategy.offsetAsLong(actorSys, "raffle-view")
        )

      }
  }




  lazy val inMemoryBackend = {
    val backend = new InMemoryBackend
    backend
      .configure {
        // aggregate config - write model
        aggregate(Raffle.behavior)
      }
      .configure {
        projection(
          projection = new RaffleViewProjection(raffleViewRepo),
          publisherFactory = backend.inMemoryPublisherFactory[RaffleEvent]
        )
      }
  }

}
