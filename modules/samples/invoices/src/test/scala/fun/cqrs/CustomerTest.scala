package fun.cqrs

import invoices.domain.model.{Country, City, Street, Address}
import org.scalatest.{Matchers, FunSuite}

class CustomerTest extends FunSuite with Matchers {

  test("Customer test") {

    new CustomerFixture {

      val view =
        createCustomer("João", Address(Street("Rua Conde de Bonfim"), City("Rio"), Country("Brazil")))
          .changeName("Paulo")
          .changeStreet(Street("Av. Viera Souto"))
          .view

    }

  }
}
