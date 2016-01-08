package lottery.domain.service

import akka.actor.ActorSystem
import akka.util.Timeout
import io.funcqrs.backend.akka.api._
import lottery.app.LevelDbTaggedEventsSource
import lottery.domain.model.Lottery

import scala.concurrent.duration._

trait LotteryModule {

  def actorSystem: ActorSystem

  implicit val timeout = Timeout(3.seconds)
  implicit lazy val backend = new AkkaBackend(actorSystem)

  //----------------------------------------------------------------------
  // WRITE side wiring
  val lotteryService =
    configure {
      aggregate[Lottery](Lottery.behavior)
    }.join("LotteryViewProjection")

  //----------------------------------------------------------------------
  // READ side wiring
  val lotteryViewRepo = new LotteryViewRepo

  configure {
    projection(
      sourceProvider = new LevelDbTaggedEventsSource(Lottery.tag),
      projection = new LotteryViewProjection(lotteryViewRepo),
      name = "LotteryViewProjection"
    )
  }


}
