package lottery.app

import io.funcqrs.backend.{ QueryByTag, Backend }
import io.funcqrs.config.Api._
import lottery.domain.model.Lottery
import lottery.domain.service.{ LotteryViewRepo, LotteryViewProjection }

import scala.language.higherKinds

object LotteryBackendConfig {

  def apply[F[_]](backend: Backend[F], repo: LotteryViewRepo) = {

    backend
      // ---------------------------------------------
      // aggregate config - write model
      .configure {
        aggregate[Lottery](Lottery.behavior)
      }
      // end::lottery-actor[]

      // ---------------------------------------------
      // projection config - read model
      .configure {
        projection(
          query = QueryByTag(Lottery.tag),
          projection = new LotteryViewProjection(repo),
          name = "LotteryViewProjection"
        )
      }
  }
}
