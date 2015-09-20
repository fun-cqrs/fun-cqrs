package shop.domain.service

import com.softwaremill.macwire._
import fun.cqrs.{LoggingSuffix, Logging, HandleEvent, Projection}
import shop.domain.model.OrderProtocol._
import shop.domain.model._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class OrderViewProjection(orderRepo: OrderViewRepo,
                          productRepo: ProductViewRepo @@ OrderView.type,
                          customerRepo: CustomerViewRepo @@ OrderView.type) extends Projection with Logging {


  // reuse projections with other repos
  val productProjection = new ProductViewProjection(productRepo) with LoggingSuffix {
    val suffix = "OrderView"
  }

  val customerProjection = new CustomerViewProjection(customerRepo) with LoggingSuffix {
    val suffix = "OrderView"
  }
  

  def receiveEvent: HandleEvent = {

    case e: ProductProtocol.ProductEvent =>
      logger.debug(s"received product event $e")
      productProjection.onEvent(e)

    case e: CustomerProtocol.CustomerEvent =>
      logger.debug(s"received customer event $e")
      customerProjection.onEvent(e)

    case e: OrderProtocol.OrderCreated   => create(e)
    case e: OrderProtocol.ProductAdded   => addProduct(e)
    case e: OrderProtocol.ProductRemoved => removeProduct(e)
    case e: OrderProtocol.OrderExecuted  => execute(e)
    case e: OrderProtocol.OrderCancelled => cancel(e)

  }

  private def number(evt: OrderProtocol.OrderEvent) = {
    OrderNumber.fromAggregateId(evt.metadata.aggregateId)
  }

  def create(evt: OrderCreated): Future[Unit] = {
    logger.debug(s"creating order $evt")
    customerRepo.find(evt.customerId).flatMap { customer =>
      orderRepo.save(OrderView(number(evt), customer.name))
    }
  }

  def execute(evt: OrderExecuted): Future[Unit] = {
    val num = number(evt)
    logger.debug(s"executing: $num")
    for {
      order <- orderRepo.find(num)
      updatedOrder = order.copy(status = Executed)
      _ <- orderRepo.save(updatedOrder)
    } yield ()
  }

  def cancel(evt: OrderCancelled): Future[Unit] = {
    val num = number(evt)
    logger.debug(s"cancelling: $num")
    for {
      order <- orderRepo.find(num)
      _ <- orderRepo.save(order.copy(status = Cancelled))

    } yield ()
  }

  def addProduct(evt: ProductAdded): Future[Unit] = {

    val num = number(evt)
    logger.debug(s"adding product ${evt.productNumber} to order $num")

    for {
      order <- orderRepo.find(num)
      product <- productRepo.find(evt.productNumber)
      newItem = OrderItem(evt.productNumber, product.name, product.price, Quantity(1))
      _ <- orderRepo.save(order.addItem(newItem))
    } yield ()
  }


  def removeProduct(evt: ProductRemoved): Future[Unit] = {

    val num = number(evt)
    logger.debug(s"removing product ${evt.productNumber} from order $num")

    for {
      order <- orderRepo.find(num)
      product <- productRepo.find(evt.productNumber)
      _ <- orderRepo.save(order.removeItem(evt.productNumber))
    } yield ()
  }
}
