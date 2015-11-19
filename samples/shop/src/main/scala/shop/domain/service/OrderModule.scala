package shop.domain.service

import akka.actor.{ ActorRef, Props }
import com.softwaremill.macwire._
import io.strongtyped.funcqrs.akka._
import io.strongtyped.funcqrs.{ Projection, Behavior, Tag }
import shop.api.AkkaModule
import shop.app.LevelDbTaggedEventsSource
import shop.domain.model.{ Order, OrderNumber, OrderView }

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

  // reuse projections with other repos
  val productProjection = new ProductViewProjection(productViewRepoForOrder)
  val customerProjection = new CustomerViewProjection(customerViewRepoForOrder)

  val orderViewProjection = wire[OrderViewProjection] orElse productProjection orElse customerProjection

  funCQRS.projection[OrderViewProjectionActor]("OrderViewProjectionActor", orderViewProjection)

}

class OrderAggregateManager extends AggregateManager with AssignedAggregateId {

  type Aggregate = Order

  def behavior(id: OrderNumber): Behavior[Order] = Order.behavior(id)

  override def aggregatePassivationStrategy = AggregatePassivationStrategy(maxChildren = Some(MaxChildren(40, 20)))

}

class OrderViewProjectionActor(name: String, projection: Projection)
    extends ProjectionActor(name, projection) with LevelDbTaggedEventsSource with OffsetNotPersisted {

  val tag: Tag = Order.dependentView

  override def onFailure = {
    // do nothing, ignore event
    case (evt, e: NoSuchElementException) => log.debug(s"Got a NoSuchElementException, ignoring event $evt")
  }
}