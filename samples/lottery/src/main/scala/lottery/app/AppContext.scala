package lottery.app

// import akka.actor.ActorSystem
// import io.funcqrs.akka.EventsSourceProvider
// import io.funcqrs.akka.backend.AkkaBackend
import io.funcqrs.backend.{ Backend, Query, QueryByTag, QuerySelectAll }
import io.funcqrs.config.Api._
import io.funcqrs.test.backend.InMemoryBackend
import lottery.domain.model.Lottery
//import lottery.domain.service.{ LevelDbTaggedEventsSource, LotteryViewProjection, LotteryViewRepo }
import lottery.domain.service.{ LotteryViewProjection, LotteryViewRepo }

import scala.language.higherKinds

object AppContext {

  private var isAkkaConfigured = false
  val lotteryViewRepo = new LotteryViewRepo

  // // tag::akka-backend[]
  // private lazy val _akkaBackend = new AkkaBackend { // #<1>
  //   val actorSystem: ActorSystem = ActorSystem("FunCQRS") // #<2>
  //   def sourceProvider(query: Query): EventsSourceProvider = { // #<3>
  //     query match {
  //       case QueryByTag(tag) => new LevelDbTaggedEventsSource(tag)
  //     }
  //   }
  // }
  // // end::akka-backend[]

  // lazy val akkaBackend = {
  //   isAkkaConfigured = true
  //   configure(_akkaBackend)
  // }
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
    //if (isAkkaConfigured) akkaBackend.actorSystem.terminate()
  }
}
