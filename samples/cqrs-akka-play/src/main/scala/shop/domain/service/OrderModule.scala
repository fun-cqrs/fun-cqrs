package shop.domain.service

import akka.actor.{ActorRef, Props}
import com.softwaremill.macwire._
import io.strongtyped.funcqrs.akka._
import io.strongtyped.funcqrs.{Behavior, Tag}
import shop.api.AkkaModule
import shop.app.LevelDbProjectionSource
import shop.domain.model.{Order, OrderNumber, OrderView}

trait OrderModule extends AkkaModule {

  val orderAggregateManager: ActorRef @@ Order.type =
    actorSystem
      .actorOf(Props[OrderAggregateManager], "orderAggregateManager")
      .taggedWith[Order.type]


  //----------------------------------------------------------------------
  // READ side wiring
  val orderViewRepo = wire[OrderViewRepo]
  val productViewRepoForOrder = wire[ProductViewRepo].taggedWith[OrderView.type]
  val customerViewRepoForOrder = wire[CustomerViewRepo].taggedWith[OrderView.type]

  funCQRS.projection[OrderViewProjectionActor]("OrderViewProjectionActor", wire[OrderViewProjection])

}

class OrderAggregateManager extends AggregateManager with AssignedAggregateId {
  type AggregateType = Order
  def behavior(id: OrderNumber): Behavior[Order] = Order.behavior(id)

  override def aggregatePassivationStrategy = AggregatePassivationStrategy(maxChildren = Some(MaxChildren(40, 20)))

}

class OrderViewProjectionActor(name: String, projection: OrderViewProjection)
  extends ProjectionActor(name, projection) with LevelDbProjectionSource {

  val tag: Tag = Order.dependentView

  override def onFailure = {
    // do nothing, ignore event
    case (evt, e: NoSuchElementException) => log.debug(s"Got a NoSuchElementException, ignoring event $evt")
  }
}