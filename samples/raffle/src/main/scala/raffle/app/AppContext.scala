package raffle.app

import akka.actor.ActorSystem
import io.funcqrs.akka.EventsSourceProvider
import io.funcqrs.akka.backend.AkkaBackend
import io.funcqrs.backend.{ Backend, Query, QuerySelectAll }
import io.funcqrs.config.Api._
import io.funcqrs.test.backend.InMemoryBackend
import raffle.domain.model.Raffle
import raffle.domain.service.{ LevelDbTaggedEventsSource, RaffleViewProjection, RaffleViewRepo }

import scala.language.higherKinds

object AppContext {

  private var isAkkaConfigured = false
  val raffleViewRepo = new RaffleViewRepo

  private lazy val _akkaBackend = new AkkaBackend {
    val actorSystem: ActorSystem = ActorSystem("FunCQRS")
    def sourceProvider(query: Query): EventsSourceProvider = {
      query match {
        case _ => new LevelDbTaggedEventsSource(Raffle.tag)
      }
    }
  }

  lazy val akkaBackend = {
    isAkkaConfigured = true
    configure(_akkaBackend)
  }

  lazy val inMemoryBackend = configure(new InMemoryBackend)

  def configure[F[_]](backend: Backend[F]): backend.type = {

    backend.configure {
      // aggregate config - write model
      aggregate(Raffle.behavior)
    }

    backend.configure {
      // projection config - read model
      projection(
        query      = QuerySelectAll,
        projection = new RaffleViewProjection(raffleViewRepo),
        name       = "RaffleViewProjection"
      )
    }

    backend
  }

  def close = {
    if (isAkkaConfigured) akkaBackend.actorSystem.terminate()
  }
}
