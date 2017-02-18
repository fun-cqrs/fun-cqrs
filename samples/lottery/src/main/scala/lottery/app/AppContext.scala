package lottery.app

import akka.actor.ActorSystem
import io.funcqrs.akka.EventsSourceProvider
import io.funcqrs.akka.backend.AkkaBackend
import io.funcqrs.backend.{ Backend, Query, QueryByTag, QuerySelectAll }
import io.funcqrs.config.Api._
import io.funcqrs.test.backend.InMemoryBackend
import lottery.domain.model.Lottery
import lottery.domain.service.{ LevelDbTaggedEventsSource, LotteryViewProjection, LotteryViewRepo }

import scala.language.higherKinds

object AppContext {

  private var isAkkaConfigured = false
  val lotteryViewRepo = new LotteryViewRepo

  private lazy val _akkaBackend = new AkkaBackend {
    val actorSystem: ActorSystem = ActorSystem("FunCQRS")
    def sourceProvider(query: Query): EventsSourceProvider = {
      query match {
        case QuerySelectAll => new LevelDbTaggedEventsSource(Lottery.tag)
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
      aggregate(Lottery.behavior)
    }

    backend.configure {
      // projection config - read model
      projection(
        query      = QuerySelectAll,
        projection = new LotteryViewProjection(lotteryViewRepo),
        name       = "LotteryViewProjection"
      )
    }

    backend
  }

  def close = {
    if (isAkkaConfigured) akkaBackend.actorSystem.terminate()
  }
}
