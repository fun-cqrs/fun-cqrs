package shop.domain.model

import java.time.OffsetDateTime

import fun.cqrs._
import fun.cqrs.dsl.BehaviorDsl._
import fun.cqrs.json.TypedJson.{TypeHintFormat, _}
import play.api.libs.json.Json

sealed trait Status

case object Open extends Status

case object Executed extends Status

case object Cancelled extends Status

case class Order(number: OrderNumber,
                 customerId: CustomerId,
                 products: Map[ProductNumber, Quantity] = Map(),
                 status: Status = Open) extends Aggregate {

  type Identifier = OrderNumber
  def identifier: Identifier = number
  type Protocol = OrderProtocol.type

  def addProduct(productNumber: ProductNumber): Order = {
    val qty = products.getOrElse(productNumber, Quantity(0))
    val newProducts = products + (productNumber -> qty.plusOne)
    copy(products = newProducts)
  }

  def removeProduct(productNumber: ProductNumber): Order = {

    val maybeQuantity =
      products.get(productNumber).flatMap {
        case Quantity(1) => None
        case Quantity(0) => None
        case qty         => Some(qty.minusOne)
      }

    val newProducts =
      maybeQuantity.map { qty =>
        // if it's a Some, we have a qty >= 1, we update the map
        products + (productNumber -> qty)
      }.getOrElse {
        // if quantity reaches 0, we remove it from Map
        products.filterKeys(_ != productNumber)
      }

    copy(products = newProducts)
  }
}

case class Quantity(num: Int) {
  def plusOne = Quantity(num + 1)
  def minusOne = Quantity(num - 1)
}

object Quantity {
  implicit val format = Json.format[Quantity]
}

object Order {

  val tag = Tags.aggregateTag("order")
  val dependentView = Tags.dependentViews("OrderView")


  def behavior(orderNum: OrderNumber): Behavior[Order] = {

    import OrderProtocol._
    val metadata = Metadata.metadata(tag, dependentView)

    behaviorFor[Order].whenConstructing { it =>

      it.yieldsEvent {
        case cmd: CreateOrder => OrderCreated(cmd.customerId, metadata(orderNum))
      }

      it.acceptsEvents {
        case evt: OrderCreated => Order(orderNum, evt.customerId)
      }

    }.whenUpdating { it =>

      it.yieldsSingleEvent {

        case (order, cmd: AddProduct) if order.status == Open =>
          ProductAdded(cmd.productNumber, metadata(orderNum))

        case (order, cmd: RemoveProduct) if order.status == Open =>
          ProductRemoved(cmd.productNumber, metadata(orderNum))

        case (order, cmd: Execute) if order.status == Open =>
          OrderExecuted(OffsetDateTime.now(), metadata(orderNum))

        case (order, cmd: Cancel) if order.status == Open =>
          OrderCancelled(OffsetDateTime.now(), metadata(orderNum))
      }

      it.rejectsCommands {

        case (order, cmd: Execute) if order.status == Executed =>
          new CommandException(s"Order is already executed")

        case (order, cmd: Execute) if order.status == Cancelled =>
          new CommandException(s"Can't execute a cancelled order")

        case (order, cmd: Cancel) if order.status == Executed =>
          new CommandException(s"Can't cancel an executed order")

        case (order, _) if order.status == Executed =>
          new CommandException(s"Can't modify an executed order")

        case (order, _) if order.status == Cancelled =>
          new CommandException(s"Can't modify a cancelled order")


      }

      it.acceptsEvents {

        case (order, evt: ProductAdded) => order.addProduct(evt.productNumber)

        case (order, evt: ProductRemoved) => order.removeProduct(evt.productNumber)

        case (order, evt: OrderExecuted)  => order.copy(status = Executed)
        case (order, evt: OrderCancelled) => order.copy(status = Cancelled)
      }
    }
  }
}

case class OrderNumber(value: String) extends AggregateIdentifier

object OrderNumber {
  implicit val format = Json.format[OrderNumber]
  def fromAggregateId(aggregateId: AggregateIdentifier) = OrderNumber.fromString(aggregateId.value)
  def fromString(id: String) = OrderNumber(id)
}

object OrderProtocol extends ProtocolDef.Commands with ProtocolDef.Events {

  sealed trait OrderCommand extends DomainCommand

  case class CreateOrder(customerId: CustomerId) extends OrderCommand with CreateCmd

  case class AddProduct(productNumber: ProductNumber) extends OrderCommand with UpdateCmd

  case class RemoveProduct(productNumber: ProductNumber) extends OrderCommand with UpdateCmd

  case class Execute(bool: Boolean = true) extends OrderCommand with UpdateCmd

  case class Cancel(bool: Boolean = true) extends OrderCommand with UpdateCmd

  implicit val commandFormats = {
    TypeHintFormat[OrderCommand](
      Json.format[CreateOrder].withTypeHint("Order.Create"),
      Json.format[AddProduct].withTypeHint("Order.AddProduct"),
      Json.format[RemoveProduct].withTypeHint("Order.RemoveProduct"),
      Json.format[Execute].withTypeHint("Order.Execute"),
      Json.format[Cancel].withTypeHint("Order.Cancel")
    )
  }

  sealed trait OrderEvent extends DomainEvent

  case class OrderCreated(customerId: CustomerId, metadata: Metadata) extends OrderEvent with CreateEvent

  case class ProductAdded(productNumber: ProductNumber, metadata: Metadata) extends OrderEvent with UpdateEvent

  case class ProductRemoved(productNumber: ProductNumber, metadata: Metadata) extends OrderEvent with UpdateEvent

  case class OrderExecuted(date: OffsetDateTime, metadata: Metadata) extends OrderEvent with UpdateEvent

  case class OrderCancelled(date: OffsetDateTime, metadata: Metadata) extends OrderEvent with UpdateEvent

}