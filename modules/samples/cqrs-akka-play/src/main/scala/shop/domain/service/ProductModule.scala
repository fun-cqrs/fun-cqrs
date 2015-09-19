package shop.domain.service

import akka.actor.{ActorRef, Props}
import com.softwaremill.macwire._
import fun.cqrs.Tag
import fun.cqrs.akka.{ProjectionActor, AggregateManager, AssignedAggregateId}
import shop.api.AkkaModule
import shop.app.LevelDbProjectionSource
import shop.domain.model.{ProductView, Product, ProductNumber}
import scala.concurrent.ExecutionContext.Implicits.global


trait ProductModule {
  this: AkkaModule =>

  // WRITE side wiring
  val productAggregateManager: ActorRef @@ Product.type =
    actorSystem
      .actorOf(Props[ProductAggregateManager], "ProductAggregateManager")
      .taggedWith[Product.type]


  //----------------------------------------------------------------------
  // READ side wiring
  val productViewRepo = wire[ProductViewRepo].taggedWith[ProductView.type]
  val productViewProjectionActor: ActorRef =
    actorSystem
      .actorOf(Props(classOf[ProductViewProjectionActor], wire[ProductViewProjection]), "productViewProjectionActor")

}

class ProductAggregateManager extends AggregateManager with AssignedAggregateId {
  type AggregateType = Product
  def behavior(id: ProductNumber) = Product.behavior(id)
}

class ProductViewProjectionActor(val projection: ProductViewProjection) extends ProjectionActor with LevelDbProjectionSource {
  val tag: Tag = Product.tag
}