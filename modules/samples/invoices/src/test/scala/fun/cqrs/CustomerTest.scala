package fun.cqrs

import invoices.domain.model.{Country, City, Street, Address}
import org.scalatest.{Matchers, FunSuite}

class CustomerTest extends FunSuite with Matchers {

  test("Customer test") {

    new CustomerFixture {

      val view =
        createCustomer("abc", Address(Street("as"), City("Rio"), Country("Brazil")))
          .changeName("another name")
          .changeStreet(Street("Av. Viera Souto"))
          .view

    }

  }
}
