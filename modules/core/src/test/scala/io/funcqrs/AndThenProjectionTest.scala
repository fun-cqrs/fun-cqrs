package io.funcqrs

import org.scalatest.concurrent.{ Futures, ScalaFutures }
import org.scalatest.{ FlatSpec, Matchers, OptionValues }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AndThenProjectionTest extends FlatSpec with Matchers with Futures with ScalaFutures with OptionValues {

  implicit val patienceConf = patienceConfig

  behavior of "AndThenProjection"

  case class FooEvent(value: String) extends TestDomainEvent

  case class BarEvent(num: Int) extends TestDomainEvent

  it should "propagate events to both underlying Projection" in {

    val fooProjection1 = newFooProjection()
    val fooProjection2 = newFooProjection()

    val andThenProjection = fooProjection1 andThen fooProjection2

    whenReady(andThenProjection.onEvent(FooEvent("abc"))) { _ =>
      fooProjection1.result.value shouldBe "abc"
      fooProjection2.result.value shouldBe "abc"
    }
  }

  it should "propagate events to second Projection even when first Projection is not defined for passed Event" in {

    val fooProjection = newFooProjection()
    val barProjection = newBarProjection()

    val andThenProjection = fooProjection andThen barProjection

    whenReady(andThenProjection.onEvent(BarEvent(10))) { _ =>
      fooProjection.result shouldBe None
      barProjection.result.value shouldBe 10
    }

  }

  it should "stop propagating Event if first Projection fails" in {

    val barProjection = newBarProjection()

    val andThenProjection = newFailingProjection() andThen barProjection

    // we must recover it in other to use with ScalaTest
    val recovered = andThenProjection.onEvent(BarEvent(10)).recover { case _ => () }

    whenReady(recovered) { _ =>
      barProjection.result shouldBe None
    }
  }

  it should "second Projection is only executed if first succeeded" in {

    val barProjection = newBarProjection()

    val andThenProjection = barProjection andThen newFailingProjection()

    // we must recover it in other to use with ScalaTest
    val recovered = andThenProjection.onEvent(BarEvent(10)).recover { case _ => () }

    whenReady(recovered) { _ =>
      barProjection.result.value shouldBe 10
    }
  }

  def newFailingProjection() = new Projection {
    def handleEvent = {
      case evt => Future.failed(new IllegalArgumentException("this projection should not receive events"))
    }
  }

  trait StatefulProjection[T] extends Projection {
    var result: Option[T] = None
  }
  def newFooProjection() = new StatefulProjection[String] {
    def handleEvent = {
      case evt: FooEvent =>
        result = Some(evt.value)
        Future.successful(())
    }
  }

  def newBarProjection() = new StatefulProjection[Int] {
    def handleEvent = {
      case evt: BarEvent =>
        result = Some(evt.num)
        Future.successful(())
    }
  }
}
