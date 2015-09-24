package shop.domain.model

import org.scalatest.{FlatSpec, Matchers, TryValues}
import io.strongtyped.funcqrs.FutureTry

import shop.domain.model.ProductProtocol._

import scala.concurrent.ExecutionContext.Implicits.global

class ProductTest extends FlatSpec with Matchers with FutureTry with TryValues {

  behavior of "A Product.Behavior"

  val productBehavior = Product.behavior(ProductNumber("prod-num"))

  it should "produce a ProductCreated event when validating a valid CreateProduct command" in {

    val cmd = CreateProduct("test", "just a test", 10)
    val result = productBehavior.validate(cmd)

    // should succeed
    result.asTry.success
  }

  it should "return a Failure when receiving a invalid CreateProduct command" in {

    val cmd = CreateProduct("test", "just a test", -1)
    val result = productBehavior.validate(cmd)
    result.asTry.failure.exception.getMessage shouldBe "Price is too low!"
  }

  it should "build a Product when applying a valid CreateProduct command" in {

    val cmd = CreateProduct("test", "just a test", 10)
    val result = productBehavior.applyCommand(cmd)
    result.asTry.success
  }

  it should "update the Product's name when applying a valid ChangeName command" in {

    val cmd = CreateProduct("test", "just a test", 10)

    val result =
      for {
        (_, prod) <- productBehavior.applyCommand(CreateProduct("test", "just a test", 10))
        (_, updated) <- productBehavior.applyCommand(prod, ChangeName("abc"))
      } yield updated

    result.asTry.success

    result.asTry.success.value.name shouldBe "abc"
  }

  it should "refuse to update a Product's price when applying a invalid ChangePrice command" in {

    val cmd = CreateProduct("test", "just a test", 10)

    val result =
      for {
        (_, prod) <- productBehavior.applyCommand(CreateProduct("test", "just a test", 10))
        (_, updated) <- productBehavior.applyCommand(prod, ChangePrice(-1))
      } yield updated

    result.asTry.failure.exception.getMessage shouldBe "Price is too low!"
  }
}
