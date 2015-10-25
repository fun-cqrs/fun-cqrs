package shop.domain.service

import com.softwaremill.macwire._
import com.typesafe.scalalogging.LazyLogging
import io.strongtyped.funcqrs.{ HandleEvent, Projection }
import shop.domain.model.OrderProtocol.{ OrderCreated, ProductAdded, ProductRemoved }
import shop.domain.model._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class OrderViewProjection(orderRepo: OrderViewRepo,
                          productRepo: ProductViewRepo @@ OrderView.type,
                          customerRepo: CustomerViewRepo @@ OrderView.type) extends Projection with LazyLogging {

  def receiveEvent: HandleEvent = {

    case e: OrderProtocol.OrderCreated   => create(e)
    case e: OrderProtocol.ProductAdded   => addProduct(e)
    case e: OrderProtocol.ProductRemoved => removeProduct(e)

    case e: OrderProtocol.OrderExecuted  => changeStatus(e.aggregateId, Executed)
    case e: OrderProtocol.OrderCancelled => changeStatus(e.aggregateId, Cancelled)

  }

  def create(evt: OrderCreated): Future[Unit] = {
    logger.debug(s"creating order $evt")
    customerRepo.find(evt.customerId).flatMap { customer =>
      orderRepo.save(OrderView(evt.aggregateId, customer.name))
    }
  }

  def changeStatus(num: OrderNumber, status: Status): Future[Unit] = {
    logger.debug(s"order [$num] status updated to $status ")
    for {
      order <- orderRepo.find(num)
      updatedOrder = order.copy(status = status)
      _ <- orderRepo.save(updatedOrder)
    } yield ()
  }

  def addProduct(evt: ProductAdded): Future[Unit] = {

    val num = evt.aggregateId
    logger.debug(s"adding product ${evt.productNumber} to order $num")

    for {
      order <- orderRepo.find(num)
      product <- productRepo.find(evt.productNumber)
      newItem = OrderItem(evt.productNumber, product.name, product.price, Quantity(1))
      _ <- orderRepo.save(order.addItem(newItem))
    } yield ()
  }

  def removeProduct(evt: ProductRemoved): Future[Unit] = {

    val num = evt.aggregateId
    logger.debug(s"removing product ${evt.productNumber} from order $num")

    for {
      order <- orderRepo.find(num)
      product <- productRepo.find(evt.productNumber)
      _ <- orderRepo.save(order.removeItem(evt.productNumber))
    } yield ()
  }
}
