package shop.domain.model

import org.scalatest.{FunSuite, Matchers}

class CustomerTest extends FunSuite with Matchers {

  test("Customer test") {

    new CustomerFixture {

      val view =
        createCustomer("João")
          .addAddress(Address(Street("Rua Conde de Bonfim"), City("Rio"), Country("Brazil")))
          .changeName("Paulo")
          .changeStreet(Street("Av. Viera Souto"))
          .view

    }

  }
}
