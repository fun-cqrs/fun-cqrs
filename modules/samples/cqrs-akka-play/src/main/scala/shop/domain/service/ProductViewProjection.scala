package shop.domain.service

import fun.cqrs.{Logging, HandleEvent, Projection}
import shop.domain.model.ProductProtocol.{NameChanged, PriceChanged, ProductCreated, ProductUpdateEvent}
import shop.domain.model.{ProductNumber, ProductProtocol, ProductView}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ProductViewProjection(repo: ProductViewRepo) extends Projection with Logging {


  def receiveEvent: HandleEvent = {
    case e: ProductCreated                     => create(e)
    case e: ProductProtocol.ProductUpdateEvent => update(e)
  }

  def create(e: ProductCreated): Future[Unit] = {
    logger.debug(s"Creating product $e")
    val id = ProductNumber.fromAggregateId(e.metadata.aggregateId)
    repo.save(ProductView(e.name, e.description, e.price, id))
  }

  def update(e: ProductUpdateEvent): Future[Unit] = {
    val id = ProductNumber.fromAggregateId(e.metadata.aggregateId)
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
