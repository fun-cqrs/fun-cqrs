package fun.cqrs

import invoices.domain.model.CustomerProtocol._
import invoices.domain.model._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait CustomerFixture extends EventBusSupport {

  // in-memory repo
  val inMemoryCustomerViewRepo = new CustomerViewRepo with InMemoryRepository {
    def $id(model: CustomerView): CustomerId = model.identifier
  }

  val fixture = new AggregateFixture[Customer](eventBus) {

    def projection = new CustomerViewProjection(inMemoryCustomerViewRepo)

    val behavior = Customer.behavior()
  }

  def createCustomer(name: String, address: Address): Future[Customer] = {
    val cmd = CreateCustomer(name, address)
    fixture(cmd)
  }

  implicit class CustomerOps(customerResult: Future[Customer]) {

    def changeName(name: String) = {
      customerResult.flatMap { cust =>
        val cmd = ChangeName(name)
        fixture(cust, cmd)
      }
    }

    def changeStreet(street: Street) = {
      customerResult.flatMap { cust =>
        val cmd = ChangeAddressStreet(street)
        fixture(cust, cmd)
      }
    }

    def addVatNumber(vat: VAT) = {
      customerResult.flatMap { cust =>
        val cmd = AddVatNumber(vat)
        fixture(cust, cmd)
      }
    }

    def view: Future[CustomerView] = {
      customerResult.flatMap { cust =>
        inMemoryCustomerViewRepo.find(cust.identifier)
      }
    }
  }

}
