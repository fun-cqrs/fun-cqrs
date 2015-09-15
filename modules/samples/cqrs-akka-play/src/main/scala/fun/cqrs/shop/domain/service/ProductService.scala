package fun.cqrs.shop.domain.service

import fun.cqrs.shop.domain.model.ProductProtocol.CreateProduct
import fun.cqrs.shop.domain.model.{Product, ProductId}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ProductService(repo: ProductRepo, productViewProjection: ProductViewProjection) {

  // POST
  def create(cmd: CreateProduct): Future[Unit] = {
    val behavior = Product.behavior(ProductId())

    behavior.applyCommand(cmd).flatMap { case (evt, prod) =>

      productViewProjection.receiveEvent(evt)

      repo.save(prod)
    }
  }

  // PUT
  def update(id: ProductId, cmd: Product#Protocol#UpdateCmd): Future[Unit] = {
    for {
      prod <- repo.find(id)
      (evts, updatedProd) <- Product.behavior(id).applyCommand(prod, cmd)
      _ <- repo.save(updatedProd)
    } yield {
      evts.foreach(productViewProjection.receiveEvent(_))
      ()
    }
  }
}
