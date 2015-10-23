package raffle.domain.service

import akka.actor.{ActorRef, Props}
import com.softwaremill.macwire._
import io.strongtyped.funcqrs.akka._
import io.strongtyped.funcqrs.{Behavior, Tag}
import raffle.api.AkkaModule
import raffle.app.LevelDbTaggedEventsSource
import raffle.domain.model.{Raffle, RaffleId, RaffleView}


trait RaffleModule extends AkkaModule {

  // WRITE side wiring
  val productAggregateManager: ActorRef @@ Raffle.type =
    actorSystem
      .actorOf(Props[RaffleAggregateManager], "RaffleAggregateManager")
      .taggedWith[Raffle.type]


  //----------------------------------------------------------------------
  // READ side wiring
  val productViewRepo = wire[RaffleViewRepo].taggedWith[RaffleView.type]

  funCQRS.projection[RaffleViewProjectionActor]("RaffleViewProjectionActor", wire[RaffleViewProjection])

}

class RaffleAggregateManager extends AggregateManager with AssignedAggregateId {

  type AggregateType = Raffle

  def behavior(id: RaffleId): Behavior[Raffle] = Raffle.behavior(id)

  override def aggregatePassivationStrategy = AggregatePassivationStrategy(maxChildren = Some(MaxChildren(40, 20)))
}

class RaffleViewProjectionActor(name: String, projection: RaffleViewProjection)
  extends ProjectionActor(name, projection) // receives events and forward to RaffleViewProjection
  with LevelDbTaggedEventsSource // mixin the source
  with OffsetNotPersisted {

  // no offset persistence, replay full-stream

  val tag: Tag = Raffle.tag
}