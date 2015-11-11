package shop.domain.model

import java.time.OffsetDateTime

import funcqrs.json.TypedJson
import io.strongtyped.funcqrs._
import io.strongtyped.funcqrs.dsl.BehaviorDsl._
import TypedJson.{ TypeHintFormat, _ }
import play.api.libs.json.Json
import shop.domain.model.ProductProtocol.ProductMetadata
import scala.collection.immutable

case class Customer(name: String,
                    address: Option[Address],
                    vatNumber: Option[VAT],
                    id: CustomerId) extends Aggregate {

  type Protocol = CustomerProtocol.type
  type Id = CustomerId

  def doesNotHaveVatNumber = vatNumber.isEmpty

  def hasVatNumber = vatNumber.nonEmpty
}

case class CustomerId(value: String) extends AggregateID

object CustomerId {

  implicit val formatCustomerId = Json.format[CustomerId]

  def fromString(aggregateId: String): CustomerId = {
    CustomerId(aggregateId)
  }
}

// needs validation checks
case class VAT(number: String)

object VAT {
  implicit val formatVAT = Json.format[VAT]
}

case class Street(name: String)

object Street {

  implicit val formatStreet = Json.format[Street]

}

case class City(name: String)

object City {
  implicit val formatCity = Json.format[City]
}

case class Country(name: String)

object Country {
  implicit val formatCountry = Json.format[Country]
}

case class Address(street: Street, city: City, country: Country)

object Address {
  implicit val formatAddress = Json.format[Address]
}

object CustomerProtocol extends ProtocolDef {

  sealed trait CustomerCommand extends ProtocolCommand

  // Creation Commands
  case class CreateCustomer(name: String, vatNumber: Option[VAT] = None) extends CustomerCommand

  // Update Commands
  case class ChangeName(name: String) extends CustomerCommand

  case class AddAddress(address: Address) extends CustomerCommand

  case class ChangeAddressStreet(street: Street) extends CustomerCommand

  case class ChangeAddressCity(city: City) extends CustomerCommand

  case class ChangeAddressCountry(country: Country) extends CustomerCommand

  case class AddVatNumber(vat: VAT) extends CustomerCommand

  case object RemoveVatNumber extends CustomerCommand

  case class ReplaceVatNumber(vat: VAT) extends CustomerCommand

  val commandsFormat = {

    implicit val formatCommandId = Json.format[CommandId]

    TypeHintFormat[CustomerCommand](
      Json.format[CreateCustomer].withTypeHint("Customer.Create"),
      Json.format[ChangeName].withTypeHint("Customer.ChangeName"),
      Json.format[AddAddress].withTypeHint("Customer.AddAddress"),
      Json.format[ChangeAddressStreet].withTypeHint("Customer.ChangeAddressStreet"),
      Json.format[ChangeAddressCity].withTypeHint("Customer.ChangeAddressCity"),
      Json.format[AddVatNumber].withTypeHint("Customer.AddVatNumber"),
      hintedObject(RemoveVatNumber, "Customer.RemoveVatNumber"),
      Json.format[ReplaceVatNumber].withTypeHint("Customer.ReplaceVatNumber")
    )
  }

  case class CustomerMetadata(aggregateId: CustomerId,
                              commandId: CommandId,
                              eventId: EventId = EventId(),
                              date: OffsetDateTime = OffsetDateTime.now(),
                              tags: Set[Tag] = Set()) extends Metadata with JavaTime {

    type Id = CustomerId
  }

  sealed trait CustomerEvent extends ProtocolEvent with MetadataFacet[CustomerMetadata]

  // Creation Event
  case class CustomerCreated(name: String,
                             vatNumber: Option[VAT],
                             metadata: CustomerMetadata) extends CustomerEvent

  // Update Events

  case class NameChanged(name: String, metadata: CustomerMetadata) extends CustomerEvent

  case class AddressStreetChanged(street: Street, metadata: CustomerMetadata) extends CustomerEvent

  case class AddressCityChanged(city: City, metadata: CustomerMetadata) extends CustomerEvent

  case class AddressCountryChanged(country: Country, metadata: CustomerMetadata) extends CustomerEvent

  case class VatNumberAdded(vat: VAT, metadata: CustomerMetadata) extends CustomerEvent

  case class VatNumberRemoved(metadata: CustomerMetadata) extends CustomerEvent

  case class VatNumberReplaced(vat: VAT, oldVat: VAT, metadata: CustomerMetadata) extends CustomerEvent

}

object Customer {

  val tag = Tags.aggregateTag("Customer")

  def behavior(id: CustomerId): Behavior[Customer] = {
    import CustomerProtocol._

    def metadata(customerId: CustomerId, cmd: CustomerCommand) = {
      CustomerMetadata(customerId, cmd.id, tags = Set(tag, Order.dependentView))
    }

    behaviorFor[Customer].whenConstructing { // it =>
      _.processesCommands {
        case cmd: CreateCustomer =>
          CustomerCreated(cmd.name, cmd.vatNumber, metadata(id, cmd))
      }.acceptsEvents {
        case e: CustomerCreated =>
          Customer(e.name, address = None, e.vatNumber, id)
      }

    }.whenUpdating { // it =>

      _.processesCommands {

        case (_, cmd: ChangeName)          => NameChanged(cmd.name, metadata(id, cmd))
        case (_, cmd: ChangeAddressStreet) => AddressStreetChanged(cmd.street, metadata(id, cmd))

        case (customer, cmd: ReplaceVatNumber) if customer.hasVatNumber =>
          VatNumberReplaced(cmd.vat, customer.vatNumber.get, metadata(id, cmd))

        case (customer, cmd: AddVatNumber) if customer.doesNotHaveVatNumber => VatNumberAdded(cmd.vat, metadata(id, cmd))
        case (customer, cmd: RemoveVatNumber.type) if customer.hasVatNumber => VatNumberRemoved(metadata(id, cmd))

        case (_, cmd: AddAddress) =>
          immutable.Seq(
            AddressStreetChanged(cmd.address.street, metadata(id, cmd)),
            AddressCityChanged(cmd.address.city, metadata(id, cmd)),
            AddressCountryChanged(cmd.address.country, metadata(id, cmd))
          )
      }.acceptsEvents {
        case (customer, e: NameChanged)           => customer.copy(name = e.name)

        case (customer, e: AddressStreetChanged)  => customer.copy(address = customer.address.map(_.copy(street = e.street)))
        case (customer, e: AddressCityChanged)    => customer.copy(address = customer.address.map(_.copy(city = e.city)))
        case (customer, e: AddressCountryChanged) => customer.copy(address = customer.address.map(_.copy(country = e.country)))

        case (customer, e: VatNumberAdded)        => customer.copy(vatNumber = Some(e.vat))
        case (customer, e: VatNumberReplaced)     => customer.copy(vatNumber = Some(e.vat))
        case (customer, _: VatNumberRemoved)      => customer.copy(vatNumber = None)
      }
    }
  }

}

