package shop.domain.service

import com.typesafe.scalalogging.LazyLogging
import io.funcqrs.Projection
import io.funcqrs.HandleEvent
import shop.domain.model.ProductProtocol._
import shop.domain.model.ProductView

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ProductViewProjection(repo: ProductViewRepo) extends Projection with LazyLogging {

  def receiveEvent: HandleEvent = {
    case e: ProductCreated     => create(e)
    case e: ProductUpdateEvent => update(e)
  }

  def create(e: ProductCreated): Future[Unit] = {
    logger.debug(s"Creating product $e")
    repo.save(ProductView(e.name, e.description, e.price, e.metadata.aggregateId))
  }

  def update(e: ProductUpdateEvent): Future[Unit] = {

    logger.debug(s"Updating product $e")

    repo.updateById(e.aggregateId) { prod =>
      updateFunc(prod, e)
    }.map(_ => ())

  }

  private def updateFunc(view: ProductView, evt: ProductUpdateEvent): ProductView = {
    evt match {
      case e: NameChanged  => view.copy(name = e.newName)
      case e: PriceChanged => view.copy(price = e.newPrice)
    }
  }
}
