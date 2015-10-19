package shop.domain.service

import akka.actor.{ActorRef, Props}
import com.softwaremill.macwire._
import io.strongtyped.funcqrs.Behavior
import io.strongtyped.funcqrs.akka._
import shop.api.AkkaModule
import shop.app.LevelDbTaggedEventsSource
import shop.domain.model.{Customer, CustomerId, CustomerView}

trait CustomerModule extends AkkaModule {

  val customerAggregateManager: ActorRef @@ Customer.type =
    actorSystem
      .actorOf(Props[CustomerAggregateManager], "CustomerAggregateManager")
      .taggedWith[Customer.type]

  //----------------------------------------------------------------------
  // READ side wiring
  val customerViewRepo = wire[CustomerViewRepo].taggedWith[CustomerView.type]

  funCQRS.projection[CustomerViewProjectionActor]("CustomerViewProjectionActor", wire[CustomerViewProjection])
}

class CustomerAggregateManager extends AggregateManager with AssignedAggregateId {

  type AggregateType = Customer

  def behavior(id: CustomerId): Behavior[Customer] = Customer.behavior(id)

  override def aggregatePassivationStrategy = AggregatePassivationStrategy(maxChildren = Some(MaxChildren(40, 20)))

}

class CustomerViewProjectionActor(name: String, projection: CustomerViewProjection)
  extends ProjectionActor(name, projection) with LevelDbTaggedEventsSource with OffsetNotPersisted {
  val tag = Customer.tag
}