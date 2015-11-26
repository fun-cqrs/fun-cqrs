package lottery.domain.service

import com.softwaremill.macwire._
import io.funcqrs
import io.funcqrs.akka._
import lottery.api.AkkaModule
import lottery.app.LevelDbTaggedEventsSource
import lottery.domain.model.{ Lottery, LotteryView }

trait LotteryModule extends AkkaModule {

  import io.funcqrs.akka.dsl.FunCqrsDsl._
  // WRITE side wiring
  val lotteryService =
    service {
      aggregate[Lottery](Lottery.behavior)
        .withName("LotteryManager")
        .withAssignedId
    }

  //----------------------------------------------------------------------
  // READ side wiring
  val lotteryViewRepo = wire[LotteryViewRepo].taggedWith[LotteryView.type]

  funCQRS.projection[LotteryViewProjectionActor]("LotteryViewProjectionActor", wire[LotteryViewProjection])

}

class LotteryViewProjectionActor(name: String, projection: LotteryViewProjection)
    extends ProjectionActor(name, projection) // receives events and forward to RaffleViewProjection
    with LevelDbTaggedEventsSource // mixin the source
    with OffsetNotPersisted {

  // no offset persistence, replay full-stream

  val tag: funcqrs.Tag = Lottery.tag
}