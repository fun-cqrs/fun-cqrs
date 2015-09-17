package fun.cqrs.shop.domain.model

import java.util.UUID

import fun.cqrs._
import fun.cqrs.dsl.BehaviorDsl._
import fun.cqrs.shop.json.TypedJson.{TypeHintFormat, _}
import play.api.libs.json.Json

import scala.concurrent.ExecutionContext

case class Product(name: String, description: String, price: Double, identifier: ProductId) extends Aggregate {

  type Identifier = ProductId

  type Protocol = ProductProtocol.type

}

case class ProductId(uuid: UUID = UUID.randomUUID) extends AggregateUUID

object ProductId {

  implicit val format = Json.writes[ProductId]

  def fromString(aggregateId: String): ProductId = {
    ProductId(UUID.fromString(aggregateId))
  }

  def fromAggregateId(aggregateId: AggregateIdentifier): ProductId = {
    ProductId(UUID.fromString(aggregateId.value))
  }
}


object ProductProtocol extends ProtocolDef.Protocol {

  sealed trait ProductCommand extends DomainCommand

  // Creation Command
  case class CreateProduct(name: String, description: String, price: Double) extends ProductCommand with CreateCmd

  // Update Commands
  case class ChangeName(name: String) extends ProductCommand with UpdateCmd

  case class ChangePrice(price: Double) extends ProductCommand with UpdateCmd


  // Creation Event
  sealed trait ProductCreateEvent extends CreateEvent

  case class ProductCreated(name: String, description: String, price: Double,
                            metadata: Metadata) extends ProductCreateEvent


  // Update Events
  sealed trait ProductUpdateEvent extends UpdateEvent

  case class NameChanged(newName: String, metadata: Metadata) extends ProductUpdateEvent

  case class PriceChanged(newPrice: Double, metadata: Metadata) extends ProductUpdateEvent

  // play-json formats for commands
  implicit val commandsFormat = TypeHintFormat[ProductCommand](
    Json.format[CreateProduct].withTypeHint("Product.Create"),
    Json.format[ChangeName].withTypeHint("Product.ChangeName"),
    Json.format[ChangePrice].withTypeHint("Product.ChangePrice")
  )

}

object Product {

  def tag = Tags.aggregateTag("product")

  def behavior(id: ProductId = ProductId())(implicit ec: ExecutionContext): Behavior[Product] = {

    import ProductProtocol._

    val metadata = Metadata.metadata(tag)

    behaviorFor[Product].whenConstructing { it =>
      //---------------------------------------------------------------------------------
      // Creational Commands and Events
      it.yieldsEvent {
        // only accept creation if price is valid
        case cmd: CreateProduct if cmd.price > 0 =>
          ProductCreated(
            cmd.name,
            cmd.description,
            cmd.price,
            metadata(id)
          )
      }

      it.rejectsCommands {
        // can't create a product with 0 or negative price
        case createCmd: CreateProduct if createCmd.price <= 0 =>
          new CommandException("Price is too low!")
      }

      it.acceptsEvents {
        case e: ProductCreated => Product(e.name, e.description, e.price, id)
      }

    }.whenUpdating { it =>
      //---------------------------------------------------------------------------------
      // Update Commands and Events
      it.yieldsSingleEvent {
        // update name
        case (_, cmd: ChangeName) => NameChanged(cmd.name, metadata(id))
      }

      it.yieldsSingleEvent {
        // update price
        case (_, cmd: ChangePrice) if cmd.price > 0 => PriceChanged(cmd.price, metadata(id))

      }

      it.rejectsCommands {
        case (_, cmd: ChangePrice) if cmd.price <= 0 => new CommandException("Price is too low!")
      }

      it.acceptsEvents {
        case (product, e: NameChanged)  => product.copy(name = e.newName)
        case (product, e: PriceChanged) => product.copy(price = e.newPrice)
      }
      //---------------------------------------------------------------------------------
    }
  }
}