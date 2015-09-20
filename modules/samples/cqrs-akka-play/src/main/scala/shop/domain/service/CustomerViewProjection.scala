package shop.domain.service

import org.slf4j.LoggerFactory
import play.api.Logger
import shop.domain.model.{CustomerView, CustomerId}
import shop.domain.model.CustomerProtocol._
import fun.cqrs.{Logging, DomainEvent, Projection}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CustomerViewProjection(val customerViewRepo: CustomerViewRepo) extends Projection with Logging {


  private def customerId(evt: DomainEvent): CustomerId = {
    CustomerId.fromIdentifier(evt.metadata.aggregateId)
  }


  def receiveEvent = {
    case evt: CustomerCreated       => createView(evt)
    case evt: AddVatNumber          => updateById(evt)(_.copy(vatNumber = Some(evt.vat)))
    case evt: AddressStreetChanged  => updateById(evt)(_.copy(street = Some(evt.street)))
    case evt: AddressCityChanged    => updateById(evt)(_.copy(city = Some(evt.city)))
    case evt: AddressCountryChanged => updateById(evt)(_.copy(country = Some(evt.country)))
    case evt: NameChanged           => updateById(evt)(_.copy(name = evt.name))
  }

  private def updateById(evt: DomainEvent)(updateFunc: CustomerView => CustomerView): Future[Unit] = {
    customerViewRepo.updateById(customerId(evt))(updateFunc).map(_ => ())
  }


  def createView(customerCreated: CustomerCreated): Future[Unit] = {

    logger.debug(s"creating customer ${customerCreated.name}")
    
    customerViewRepo.save(
      CustomerView(
        name = customerCreated.name,
        street = None,
        city = None,
        country = None,
        vatNumber = customerCreated.vatNumber,
        identifier = customerId(customerCreated)
      )
    )
  }
}
