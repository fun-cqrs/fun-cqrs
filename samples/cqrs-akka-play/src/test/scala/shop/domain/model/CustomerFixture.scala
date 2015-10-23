package shop.domain.model

import io.strongtyped.funcqrs.{ AggregateFixture, EventBusSupport, InMemoryRepository }
import shop.domain.model.CustomerProtocol._
import shop.domain.service.{ CustomerViewProjection, CustomerViewRepo }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait CustomerFixture extends EventBusSupport {

  // in-memory repo
  val inMemoryCustomerViewRepo = new CustomerViewRepo {
  }

  val fixture = new AggregateFixture[Customer](eventBus) {

    def projection = new CustomerViewProjection(inMemoryCustomerViewRepo)

    val behavior = Customer.behavior(CustomerId("cust-test"))
  }

  def createCustomer(name: String): Future[Customer] = {
    val cmd = CreateCustomer(name)
    fixture(cmd)
  }

  implicit class CustomerOps(customerResult: Future[Customer]) {

    def addAddress(address: Address) = {
      customerResult.flatMap { cust =>
        val cmd = AddAddress(address)
        fixture(cust, cmd)
      }
    }

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
        inMemoryCustomerViewRepo.find(cust.id)
      }
    }
  }

}
