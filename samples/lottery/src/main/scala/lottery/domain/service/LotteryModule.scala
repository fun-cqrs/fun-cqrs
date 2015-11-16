package lottery.domain.service

import akka.actor.{ ActorRef, Props }
import com.softwaremill.macwire._
import io.strongtyped.funcqrs.akka._
import io.strongtyped.funcqrs.{ Behavior, Tag }
import lottery.api.AkkaModule
import lottery.app.LevelDbTaggedEventsSource
import lottery.domain.model.{ Lottery, LotteryId, LotteryView }

trait LotteryModule extends AkkaModule {

  // WRITE side wiring
  val productAggregateManager: ActorRef @@ Lottery.type =
    actorSystem
      .actorOf(Props[LotteryAggregateManager], "LotteryAggregateManager")
      .taggedWith[Lottery.type]

  //----------------------------------------------------------------------
  // READ side wiring
  val lotteryViewRepo = wire[LotteryViewRepo].taggedWith[LotteryView.type]

  funCQRS.projection[LotteryViewProjectionActor]("LotteryViewProjectionActor", wire[LotteryViewProjection])

}

class LotteryAggregateManager extends AggregateManager with AssignedAggregateId {

  type AggregateType = Lottery

  def behavior(id: LotteryId): Behavior[Lottery] = Lottery.behavior(id)

  override def aggregatePassivationStrategy = AggregatePassivationStrategy(maxChildren = Some(MaxChildren(40, 20)))
}

class LotteryViewProjectionActor(name: String, projection: LotteryViewProjection)
    extends ProjectionActor(name, projection) // receives events and forward to RaffleViewProjection
    with LevelDbTaggedEventsSource // mixin the source
    with OffsetNotPersisted {

  // no offset persistence, replay full-stream

  val tag: Tag = Lottery.tag
}