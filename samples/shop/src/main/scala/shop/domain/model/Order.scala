package shop.domain.model

import java.time.OffsetDateTime

import funcqrs.json.TypedJson
import funcqrs.json.TypedJson.{ TypeHintFormat, _ }
import io.funcqrs._
import io.funcqrs.dsl.BehaviorDsl
import io.funcqrs._
import play.api.libs.json._

sealed trait Status

case object Open extends Status

case object Executed extends Status

case object Cancelled extends Status

case class Order(number: OrderNumber,
                 customerId: CustomerId,
                 products: Map[ProductNumber, Quantity] = Map(),
                 status: Status = Open) extends AggregateLike {

  type Id = OrderNumber

  def id: Id = number

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

  val tag = Tags.aggregateTag("Order")
  val dependentView = Tags.dependentViews("OrderView")

  def behavior(orderNum: OrderNumber): Behavior[Order] = {

    import OrderProtocol._

    def metadata(orderNum: OrderNumber, orderCommand: OrderCommand): OrderMetadata = {
      OrderMetadata(orderNum, orderCommand.id, tags = Set(tag, dependentView))
    }

    val orderBehaviorDsl = new BehaviorDsl[Order]

    import orderBehaviorDsl.behaviorBuilder._

    whenConstructing { it =>
      it.processesCommands {
        case cmd: CreateOrder => OrderCreated(cmd.customerId, metadata(orderNum, cmd))
      }.acceptsEvents {
        case evt: OrderCreated => Order(orderNum, evt.customerId)
      }
    } whenUpdating { it =>
      it.processesCommands {
        case (order, cmd: Execute.type) if order.status == Executed =>
          new CommandException(s"Order is already executed")
        case (order, cmd: Execute.type) if order.status == Cancelled =>
          new CommandException(s"Can't execute a cancelled order")
        case (order, cmd: Cancel.type) if order.status == Executed =>
          new CommandException(s"Can't cancel an executed order")
        case (order, _) if order.status == Executed =>
          new CommandException(s"Can't modify an executed order")
        case (order, _) if order.status == Cancelled =>
          new CommandException(s"Can't modify a cancelled order")
        case (order, cmd: AddProduct) if order.status == Open =>
          ProductAdded(cmd.productNumber, metadata(orderNum, cmd))
        case (order, cmd: RemoveProduct) if order.status == Open =>
          ProductRemoved(cmd.productNumber, metadata(orderNum, cmd))
        case (order, cmd: Execute.type) if order.status == Open =>
          OrderExecuted(metadata(orderNum, cmd))
        case (order, cmd: Cancel.type) if order.status == Open =>
          OrderCancelled(metadata(orderNum, cmd))
      } acceptsEvents {
        case (order, evt: ProductAdded) =>
          order.addProduct(evt.productNumber)
        case (order, evt: ProductRemoved) =>
          order.removeProduct(evt.productNumber)
        case (order, evt: OrderExecuted) =>
          order.copy(status = Executed)
        case (order, evt: OrderCancelled) =>
          order.copy(status = Cancelled)
      }
    }
  }
}

case class OrderNumber(value: String) extends AggregateID

object OrderNumber {
  implicit val format = Json.format[OrderNumber]

  def fromString(id: String) = OrderNumber(id)
}

object OrderProtocol extends ProtocolLike {

  sealed trait OrderCommand extends ProtocolCommand

  case class CreateOrder(customerId: CustomerId) extends OrderCommand

  case class AddProduct(productNumber: ProductNumber) extends OrderCommand

  case class RemoveProduct(productNumber: ProductNumber) extends OrderCommand

  case object Execute extends OrderCommand

  case object Cancel extends OrderCommand

  implicit val commandFormats = {
    TypeHintFormat[OrderCommand](
      Json.format[CreateOrder].withTypeHint("Order.Create"),
      Json.format[AddProduct].withTypeHint("Order.AddProduct"),
      Json.format[RemoveProduct].withTypeHint("Order.RemoveProduct"),
      hintedObject(Execute, "Order.Execute"),
      hintedObject(Cancel, "Order.Cancel")
    )

  }

  case class OrderMetadata(aggregateId: OrderNumber,
                           commandId: CommandId,
                           eventId: EventId = EventId(),
                           date: OffsetDateTime = OffsetDateTime.now(),
                           tags: Set[Tag] = Set()) extends Metadata with JavaTime {

    type Id = OrderNumber
  }

  sealed trait OrderEvent extends ProtocolEvent with MetadataFacet[OrderMetadata]

  case class OrderCreated(customerId: CustomerId, metadata: OrderMetadata) extends OrderEvent

  case class ProductAdded(productNumber: ProductNumber, metadata: OrderMetadata) extends OrderEvent

  case class ProductRemoved(productNumber: ProductNumber, metadata: OrderMetadata) extends OrderEvent

  case class OrderExecuted(metadata: OrderMetadata) extends OrderEvent

  case class OrderCancelled(metadata: OrderMetadata) extends OrderEvent

}