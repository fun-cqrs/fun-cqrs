package shop.domain.model

import java.util.UUID
import fun.cqrs._
import fun.cqrs.dsl.BehaviorDsl._
import fun.cqrs.json.TypedJson.TypeHintFormat
import fun.cqrs.json.TypedJson._
import play.api.libs.json.Json
import scala.collection.immutable
import scala.concurrent.ExecutionContext

case class Customer(name: String,
                    address: Option[Address],
                    vatNumber: Option[VAT],
                    identifier: CustomerId) extends Aggregate {

  type Protocol = CustomerProtocol.type
  type Identifier = CustomerId

  def doesNotHaveVatNumber = vatNumber.isEmpty

  def hasVatNumber = vatNumber.nonEmpty
}

case class CustomerId(uuid: UUID = UUID.randomUUID()) extends AggregateUUID

object CustomerId {

  implicit val formatCustomerId = Json.format[CustomerId]

  def fromIdentifier(id: AggregateIdentifier): CustomerId = CustomerId.fromString(id.value)

  def fromString(aggregateId: String): CustomerId = {
    CustomerId(UUID.fromString(aggregateId))
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


object CustomerProtocol extends ProtocolDef.Protocol {


  sealed trait CustomerCommand extends DomainCommand

  // Creation Commands
  case class CreateCustomer(name: String, vatNumber: Option[VAT] = None) extends CustomerCommand with CreateCmd


  // Update Commands
  case class ChangeName(name: String) extends CustomerCommand with UpdateCmd

  case class AddAddress(address: Address) extends CustomerCommand with UpdateCmd

  case class ChangeAddressStreet(street: Street) extends CustomerCommand with UpdateCmd

  case class ChangeAddressCity(city: City) extends CustomerCommand with UpdateCmd

  case class ChangeAddressCountry(country: Country) extends CustomerCommand with UpdateCmd

  case class AddVatNumber(vat: VAT) extends CustomerCommand with UpdateCmd

  case class RemoveVatNumber(bool: Boolean = true) extends CustomerCommand with UpdateCmd

  case class ReplaceVatNumber(vat: VAT) extends CustomerCommand with UpdateCmd

  val commandsFormat = {

    implicit val formatCommandId = Json.format[CommandId]


    TypeHintFormat[CustomerCommand](
      Json.format[CreateCustomer].withTypeHint("Customer.Create"),
      Json.format[ChangeName].withTypeHint("Customer.ChangeName"),
      Json.format[AddAddress].withTypeHint("Customer.AddAddress"),
      Json.format[ChangeAddressStreet].withTypeHint("Customer.ChangeAddressStreet"),
      Json.format[ChangeAddressCity].withTypeHint("Customer.ChangeAddressCity"),
      Json.format[AddVatNumber].withTypeHint("Customer.AddVatNumber"),
      Json.format[RemoveVatNumber].withTypeHint("Customer.RemoveVatNumber"),
      Json.format[ReplaceVatNumber].withTypeHint("Customer.ReplaceVatNumber")
    )
  }


  // Creation Event
  sealed trait CustomerCreateEvent extends CreateEvent

  case class CustomerCreated(name: String,
                             vatNumber: Option[VAT],
                             metadata: Metadata) extends CustomerCreateEvent

  // Update Events
  sealed trait CustomerUpdateEvent extends UpdateEvent

  case class NameChanged(name: String, metadata: Metadata) extends CustomerUpdateEvent

  case class AddressStreetChanged(street: Street, metadata: Metadata) extends CustomerUpdateEvent

  case class AddressCityChanged(city: City, metadata: Metadata) extends CustomerUpdateEvent

  case class AddressCountryChanged(country: Country, metadata: Metadata) extends CustomerUpdateEvent

  case class VatNumberAdded(vat: VAT, metadata: Metadata) extends CustomerUpdateEvent

  case class VatNumberRemoved(metadata: Metadata) extends CustomerUpdateEvent

  case class VatNumberReplaced(vat: VAT, oldVat: VAT, metadata: Metadata) extends CustomerUpdateEvent

}

object Customer {

  val tag = Tags.aggregateTag("customer")

  def behavior(id: CustomerId = CustomerId())(implicit ec: ExecutionContext): Behavior[Customer] = {
    import CustomerProtocol._


    val metadata = Metadata.metadata(tag)

    behaviorFor[Customer].whenConstructing { it =>
      it.yieldsEvent {
        case cmd: CreateCustomer =>
          CustomerCreated(cmd.name, cmd.vatNumber, metadata(id))
      }

      it.acceptsEvents {
        case e: CustomerCreated =>
          Customer(e.name, address = None, e.vatNumber, id)
      }

    }.whenUpdating { it =>

      it.yieldsSingleEvent {

        case (_, cmd: ChangeName)          => NameChanged(cmd.name, metadata(id))
        case (_, cmd: ChangeAddressStreet) => AddressStreetChanged(cmd.street, metadata(id))

        case (customer, cmd: ReplaceVatNumber) if customer.hasVatNumber =>
          VatNumberReplaced(cmd.vat, customer.vatNumber.get, metadata(id))

        case (customer, cmd: AddVatNumber) if customer.doesNotHaveVatNumber    => VatNumberAdded(cmd.vat, metadata(id))
        case (customer, cmd: RemoveVatNumber) if customer.doesNotHaveVatNumber => VatNumberRemoved(metadata(id))

      }

      it.yieldsManyEvents {
        case (_, cmd: AddAddress) =>
          immutable.Seq(
            AddressStreetChanged(cmd.address.street, metadata(id)),
            AddressCityChanged(cmd.address.city, metadata(id)),
            AddressCountryChanged(cmd.address.country, metadata(id))
          )
      }

      it.acceptsEvents {
        case (customer, e: NameChanged) => customer.copy(name = e.name)

        case (customer, e: AddressStreetChanged)  => customer.copy(address = customer.address.map(_.copy(street = e.street)))
        case (customer, e: AddressCityChanged)    => customer.copy(address = customer.address.map(_.copy(city = e.city)))
        case (customer, e: AddressCountryChanged) => customer.copy(address = customer.address.map(_.copy(country = e.country)))

        case (customer, e: VatNumberAdded)    => customer.copy(vatNumber = Some(e.vat))
        case (customer, e: VatNumberReplaced) => customer.copy(vatNumber = Some(e.vat))
        case (customer, _: VatNumberRemoved)  => customer.copy(vatNumber = None)
      }
    }
  }

}

