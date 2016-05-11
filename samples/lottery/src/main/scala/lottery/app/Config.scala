package lottery.app

import akka.actor.ActorSystem
import io.funcqrs.akka.EventsSourceProvider
import io.funcqrs.akka.backend.AkkaBackend
import io.funcqrs.backend.{ QueryByTag, Query }
import lottery.domain.model.LotteryId
import lottery.domain.service.LevelDbTaggedEventsSource

object Config {

  val id = LotteryId("codemotion")

  val backend = new AkkaBackend {
    val actorSystem: ActorSystem = ActorSystem("FunCQRS")
    def sourceProvider(query: Query): EventsSourceProvider = {
      query match {
        case QueryByTag(tag) => new LevelDbTaggedEventsSource(tag)
      }
    }
  }
}
