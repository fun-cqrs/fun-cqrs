package shop.domain.service

import akka.actor.{ActorRef, Props}
import com.softwaremill.macwire._
import io.strongtyped.funcqrs.akka._
import io.strongtyped.funcqrs.{Behavior, Tag}
import shop.api.AkkaModule
import shop.app.LevelDbProjectionSource
import shop.domain.model.{Product, ProductNumber, ProductView}


trait ProductModule extends AkkaModule {

  // WRITE side wiring
  val productAggregateManager: ActorRef @@ Product.type =
    actorSystem
      .actorOf(Props[ProductAggregateManager], "ProductAggregateManager")
      .taggedWith[Product.type]


  //----------------------------------------------------------------------
  // READ side wiring
  val productViewRepo = wire[ProductViewRepo].taggedWith[ProductView.type]

  funCQRS.projection(Props(classOf[ProductViewProjectionActor], wire[ProductViewProjection]), "ProductViewProjectionActor")

}

class ProductAggregateManager extends AggregateManager with AssignedAggregateId {
  type AggregateType = Product
  def behavior(id: ProductNumber): Behavior[Product] = Product.behavior(id)

  override def aggregatePassivationStrategy = AggregatePassivationStrategy(maxChildren = Some(MaxChildren(40, 20)))
}

class ProductViewProjectionActor(val projection: ProductViewProjection) extends ProjectionActor with LevelDbProjectionSource {
  val tag: Tag = Product.tag
}