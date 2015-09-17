package fun.cqrs.shop.domain.service

import fun.cqrs.shop.domain.model.Product
import fun.cqrs.{Tag, Projection}
import fun.cqrs.akka.ProjectionActor
import fun.cqrs.shop.app.LevelDbProjectionSource

class ProductViewProjectionActor(val projection: Projection) extends ProjectionActor with LevelDbProjectionSource {

  val tag: Tag = Product.tag
}
