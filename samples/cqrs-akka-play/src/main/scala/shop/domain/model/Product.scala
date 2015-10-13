package shop.domain.model

import java.time.OffsetDateTime
import funcqrs.json.TypedJson

import scala.collection.immutable
import io.strongtyped.funcqrs._
import io.strongtyped.funcqrs.dsl.BehaviorDsl._
import TypedJson.{TypeHintFormat, _}
import play.api.libs.json.Json

import scala.concurrent.{Future, ExecutionContext}

// tag::prod[]
case class Product(name: String,
                   description: String,
                   price: Double,
                   id: ProductNumber) extends Aggregate {

  type Id = ProductNumber
  type Protocol = ProductProtocol.type

}

case class ProductNumber(number: String) extends AggregateID {
  val value = number
}
// end::prod[]

object ProductNumber {

  implicit val format = Json.format[ProductNumber]

  def fromString(aggregateId: String): ProductNumber = {
    ProductNumber(aggregateId)
  }
}

object ProductProtocol extends ProtocolDef {

  case class ProductMetadata(aggregateId: ProductNumber,
                             commandId: CommandId,
                             eventId: EventId = EventId(),
                             date: OffsetDateTime = OffsetDateTime.now(),
                             tags: Set[Tag] = Set()) extends Metadata {
    type Id = ProductNumber
  }


  sealed trait ProductCommand extends ProtocolCommand

  // Creation Command
  case class CreateProduct(name: String, description: String, price: Double) extends ProductCommand

  case class ChangePrice(price: Double) extends ProductCommand
  case class ChangeName(name: String) extends ProductCommand

  sealed trait ProductEvent extends ProtocolEvent with MetadataFacet[ProductMetadata]
  case class ProductCreated(name: String, description: String, price: Double,
                            metadata: ProductMetadata) extends ProductEvent

  sealed trait ProductUpdateEvent extends ProductEvent
  // Update Events
  case class NameChanged(newName: String, metadata: ProductMetadata) extends ProductUpdateEvent

  case class PriceChanged(newPrice: Double, metadata: ProductMetadata) extends ProductUpdateEvent


  // play-json formats for commands
  implicit val commandsFormat = {
    TypeHintFormat[ProductCommand](
      Json.format[CreateProduct].withTypeHint("Product.Create"),
      Json.format[ChangePrice].withTypeHint("Product.ChangePrice")
    )
  }

}


object Product {

  val tag = Tags.aggregateTag("Product")

  def behavior(id: ProductNumber): Behavior[Product] = behaviorImpl(id)

  private def behaviorImpl(id: ProductNumber): Behavior[Product] = {

    import ProductProtocol._

    def metadata(productNum: ProductNumber, cmd: ProductCommand) = {
      ProductMetadata(productNum, cmd.id, tags = Set(tag, Order.dependentView))
    }

    behaviorFor[Product]
      .whenConstructing { it =>

      //---------------------------------------------------------------------------------
      // Creational Commands and Events
      it.processesCommands {
        // PF (Command) => EventMagnet
        case cmd: CreateProduct if cmd.price > 0 =>
          ProductCreated(
            cmd.name,
            cmd.description,
            cmd.price,
            metadata(id, cmd)
          )

        case createCmd: CreateProduct => new CommandException("Price is too low!")
      }

      it.acceptsEvents {
        // PF (Event) => Aggregate
        case e: ProductCreated => Product(e.name, e.description, e.price, id)
      }

    }.whenUpdating { it =>

      it.processesCommands {
        // PF (Aggregate, Command) => EventMagnet
        case (prod, cmd: ChangePrice) if cmd.price < prod.price => new CommandException("Can't decrease the price")
        case (_, cmd: ChangePrice) if cmd.price <= 0            => new CommandException("Price is too low!")
        case (_, cmd: ChangePrice)                              => PriceChanged(cmd.price, metadata(id, cmd))
        case (_, cmd: ChangeName)                               => NameChanged(cmd.name, metadata(id, cmd))
      }

      it.acceptsEvents {
        // PF (Aggregate, Event) => Aggregate
        case (product, e: NameChanged)  => product.copy(name = e.newName)
        case (product, e: PriceChanged) => product.copy(price = e.newPrice)
      }
      //---------------------------------------------------------------------------------
    }
  }
}