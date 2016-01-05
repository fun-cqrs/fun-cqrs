package lottery.domain.service

import akka.actor.ActorSystem
import io.funcqrs.AggregateServiceWithAssignedId
import io.funcqrs.backend.AkkaBackend
import io.funcqrs.backend.async.api._
import lottery.domain.model.Lottery

import scala.concurrent.Future
import scala.concurrent.duration._


trait LotteryModule {

  def actorSystem: ActorSystem

  implicit lazy val backend = new AkkaBackend(actorSystem, 3.seconds)


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

//  config {
//    projection(
//      publisherProvider = new LevelDbTaggedEventsSource(Lottery.tag),
//      projection = new LotteryViewProjection(lotteryViewRepo),
//      name = "LotteryViewProjectionActor"
//    )
//  }

}
