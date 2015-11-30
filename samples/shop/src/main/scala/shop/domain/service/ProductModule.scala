package shop.domain.service

import com.softwaremill.macwire._
import io.funcqrs.akka.FunCQRS.api._
import shop.api.AkkaModule
import shop.app.LevelDbTaggedEventsSource
import shop.domain.model.{ ProductView, Product }

trait ProductModule extends AkkaModule {

  // WRITE side wiring
  val productService =
    config {
      aggregate[Product](Product.behavior)
        .withName("ProductService")
        .withAssignedId
    }

  //----------------------------------------------------------------------
  // READ side wiring
  val productViewRepo = wire[ProductViewRepo].taggedWith[ProductView.type]

  config {
    projection(
      sourceProvider = new LevelDbTaggedEventsSource(Product.tag),
      projection = wire[ProductViewProjection],
      name = "ProductViewProjection"
    ).withoutOffsetPersistence
  }

}
