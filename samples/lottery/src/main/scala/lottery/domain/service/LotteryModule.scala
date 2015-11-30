package lottery.domain.service

import akka.actor.ActorSystem
import io.funcqrs.akka.FunCQRS
import lottery.app.LevelDbTaggedEventsSource
import lottery.domain.model.Lottery

trait LotteryModule {

  def actorSystem: ActorSystem

  implicit lazy val funCQRS = new FunCQRS(actorSystem)

  import io.funcqrs.akka.FunCQRS.api._

  //----------------------------------------------------------------------
  // WRITE side wiring
  val lotteryService =
    config {
      aggregate[Lottery](Lottery.behavior)
        .withName("LotteryManager")
        .withAssignedId
    }

  //----------------------------------------------------------------------
  // READ side wiring
  val lotteryViewRepo = new LotteryViewRepo

  config {
    projection(
      sourceProvider = new LevelDbTaggedEventsSource(Lottery.tag),
      projection = new LotteryViewProjection(lotteryViewRepo),
      name = "LotteryViewProjectionActor"
    )
  }

}
