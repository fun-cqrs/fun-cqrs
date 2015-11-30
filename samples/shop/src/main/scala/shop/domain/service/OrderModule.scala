package shop.domain.service

import com.softwaremill.macwire._
import io.funcqrs.akka.FunCQRS.api._
import shop.api.AkkaModule
import shop.app.LevelDbTaggedEventsSource
import shop.domain.model.{ Order, OrderView }

import scala.concurrent.Future

trait OrderModule extends AkkaModule {

  val orderService =
    config {
      aggregate[Order](Order.behavior)
        .withName("OrderService")
        .withAssignedId
    }

  //----------------------------------------------------------------------
  // READ side wiring
  val orderViewRepo = wire[OrderViewRepo]
  val productViewRepoForOrder = wire[ProductViewRepo].taggedWith[OrderView.type]
  val customerViewRepoForOrder = wire[CustomerViewRepo].taggedWith[OrderView.type]

  // reuse projections with other repos
  val productProjection = new ProductViewProjection(productViewRepoForOrder)
  val customerProjection = new CustomerViewProjection(customerViewRepoForOrder)

  val orderViewProjection = wire[OrderViewProjection] orElse productProjection orElse customerProjection

  config {
    projection(
      sourceProvider = new LevelDbTaggedEventsSource(Order.dependentView),
      projection = orderViewProjection,
      name = "OrderViewProjection"
    ).withoutOffsetPersistence
      .onFailure {
        case (evt, e: NoSuchElementException) => Future.successful(()) // Got a NoSuchElementException, ignoring event
      }
  }

}
