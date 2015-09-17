package invoices.domain.model

import java.util.UUID
import fun.cqrs._
import fun.cqrs.dsl.BehaviorDsl._

import scala.concurrent.ExecutionContext

case class Customer(name: String,
                    address: Address,
                    vatNumber: Option[VAT],
                    identifier: CustomerId) extends Aggregate {

  type Protocol = CustomerProtocol.type
  type Identifier = CustomerId

  def doesNotHaveVatNumber = vatNumber.isEmpty

  def hasVatNumber = vatNumber.nonEmpty
}

case class CustomerId(uuid: UUID = UUID.randomUUID()) extends AggregateUUID

object CustomerId {

  def fromIdentifier(id: AggregateIdentifier): CustomerId = CustomerId(UUID.fromString(id.value))
}

// needs validation checks
case class VAT(number: String) extends AnyVal

case class Address(street: Street, city: City, country: Country)

case class Street(name: String) extends AnyVal

case class City(name: String) extends AnyVal

case class Country(name: String) extends AnyVal

object CustomerProtocol extends ProtocolDef.Protocol {

  // Creation Commands
  case class CreateCustomer(name: String, address: Address, vatNumber: Option[VAT] = None) extends CreateCmd


  // Update Commands
  case class ChangeName(name: String) extends UpdateCmd

  case class ChangeAddressStreet(street: Street) extends UpdateCmd

  case class ChangeAddressCity(city: City) extends UpdateCmd

  case class ChangeAddressCountry(country: Country) extends UpdateCmd

  case class AddVatNumber(vat: VAT) extends UpdateCmd

  case class RemoveVatNumber(override val id: CommandId = CommandId()) extends UpdateCmd

  case class ReplaceVatNumber(vat: VAT) extends UpdateCmd

  // Creation Event
  sealed trait CustomerCreateEvent extends CreateEvent

  case class CustomerCreated(name: String,
                             address: Address,
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
          CustomerCreated(cmd.name, cmd.address, cmd.vatNumber, metadata(id))
      }

      it.acceptsEvents {
        case e: CustomerCreated =>
          Customer(e.name, e.address, e.vatNumber, id)
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

      it.acceptsEvents {
        case (customer, e: NameChanged) => customer.copy(name = e.name)

        case (customer, e: AddressStreetChanged)  => customer.copy(address = customer.address.copy(street = e.street))
        case (customer, e: AddressCityChanged)    => customer.copy(address = customer.address.copy(city = e.city))
        case (customer, e: AddressCountryChanged) => customer.copy(address = customer.address.copy(country = e.country))

        case (customer, e: VatNumberAdded)    => customer.copy(vatNumber = Some(e.vat))
        case (customer, e: VatNumberReplaced) => customer.copy(vatNumber = Some(e.vat))
        case (customer, _: VatNumberRemoved)  => customer.copy(vatNumber = None)
      }
    }
  }

}

