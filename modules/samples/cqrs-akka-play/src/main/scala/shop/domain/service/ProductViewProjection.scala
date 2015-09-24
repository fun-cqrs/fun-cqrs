package shop.domain.service

import fun.cqrs.{HandleEvent, Logging, Projection}
import shop.domain.model.ProductProtocol._
import shop.domain.model.{ProductNumber, ProductView}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ProductViewProjection(repo: ProductViewRepo) extends Projection with Logging {


  def receiveEvent: HandleEvent = {
    case e: ProductCreated     => create(e)
    case e: ProductUpdateEvent => update(e)
  }

  def create(e: ProductCreated): Future[Unit] = {
    logger.debug(s"Creating product $e")
    val id = ProductNumber.fromAggregateId(e.metadata.aggregateId)
    repo.save(ProductView(e.name, e.description, e.price, id))
  }

  def update(e: ProductUpdateEvent): Future[Unit] = {
    val id = ProductNumber.fromAggregateId(e.aggregateId)
    logger.debug(s"Updating product $e")

    repo.updateById(id) { prod =>
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
