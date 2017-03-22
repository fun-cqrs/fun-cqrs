package io.funcqrs

import org.scalatest.concurrent.{ Futures, ScalaFutures }
import org.scalatest.{ FlatSpec, Matchers, OptionValues }

import scala.concurrent.ExecutionContext.Implicits.global

class OrElseProjectionTest extends FlatSpec with Matchers with Futures with ScalaFutures with OptionValues with ProjectionFixture {

  implicit val patienceConf = patienceConfig

  behavior of "OrElseProjection"

  it should "Events are not propagated to second Projection if first can handle event" in {

    val fooProjection1 = newFooProjection
    val fooProjection2 = newFooProjection

    val orElseProjection = fooProjection1 orElse fooProjection2

    whenReady(orElseProjection.onEvent(FooEvent("abc"))) { _ =>
      fooProjection1.result.value shouldBe "abc"
      fooProjection2.result shouldBe None
    }
  }

  it should "propagate events to second Projection if first Projection is not defined for passed Event" in {

    val fooProjection = newFooProjection
    val barProjection = newBarProjection

    val orElseProjection = fooProjection orElse barProjection

    whenReady(orElseProjection.onEvent(BarEvent(10))) { _ =>
      fooProjection.result shouldBe None
      barProjection.result.value shouldBe 10
    }

  }

  it should "stop propagating Event if first Projection fails" in {

    val barProjection = newBarProjection

    val orElseProjection = newFailingProjection orElse barProjection

    // we must recover it in other to use with ScalaTest
    val recovered =
      orElseProjection
        .onEvent(BarEvent(10))
        .recover { case _ => () }

    whenReady(recovered) { _ =>
      barProjection.result shouldBe None
    }
  }

}
