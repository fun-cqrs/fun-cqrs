package shop.domain.model

import fun.cqrs._
import fun.cqrs.dsl.BehaviorDsl._
import fun.cqrs.json.TypedJson.{TypeHintFormat, _}
import play.api.libs.json.Json

import scala.concurrent.ExecutionContext

// tag::prod[]
case class Product(name: String,
                   description: String,
                   price: Double,
                   identifier: ProductNumber) extends Aggregate {

  type Identifier = ProductNumber
  type Protocol = ProductProtocol.type

}

case class ProductNumber(number: String) extends AggregateIdentifier {
  val value = number
}
// end::prod[]

object ProductNumber {

  implicit val format = Json.format[ProductNumber]

  def fromAggregateId(aggregateId: AggregateIdentifier) = {
    ProductNumber(aggregateId.value)
  }

  def fromString(aggregateId: String): ProductNumber = {
    ProductNumber(aggregateId)
  }
}

object ProductProtocol extends ProtocolDef.Protocol {

  sealed trait ProductCommand extends ProtocolCommand

  // Creation Command
  case class CreateProduct(name: String, description: String, price: Double) extends ProductCommand

  // Update Commands
  case class ChangeName(name: String) extends ProductCommand

  case class ChangePrice(price: Double) extends ProductCommand


  sealed trait ProductEvent extends ProtocolEvent with MetadataFacet

  case class ProductCreated(name: String, description: String, price: Double,
                            metadata: Metadata) extends ProductEvent

  sealed trait ProductUpdateEvent extends ProductEvent
  // Update Events
  case class NameChanged(newName: String, metadata: Metadata) extends ProductUpdateEvent

  case class PriceChanged(newPrice: Double, metadata: Metadata) extends ProductUpdateEvent


  // play-json formats for commands
  implicit val commandsFormat = {
    TypeHintFormat[ProductCommand](
      Json.format[CreateProduct].withTypeHint("Product.Create"),
      Json.format[ChangeName].withTypeHint("Product.ChangeName"),
      Json.format[ChangePrice].withTypeHint("Product.ChangePrice")
    )
  }

}


object Product {

  val tag = Tags.aggregateTag("product")

  def behavior(id: ProductNumber): Behavior[Product] = {

    import ProductProtocol._

    val metadata = Metadata.metadata(tag, Order.dependentView)

    behaviorFor[Product]
      .whenConstructing { it =>

      //---------------------------------------------------------------------------------
      // Creational Commands and Events
      it.emitsEvent {
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
      it.emitsSingleEvent {
        // update name
        case (_, cmd: ChangeName) => NameChanged(cmd.name, metadata(id))
      }

      it.emitsSingleEvent {
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