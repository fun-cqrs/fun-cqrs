package io.funcqrs.akka

import akka.util.Timeout
import io.funcqrs.MissingCommandHandlerException
import io.funcqrs.akka.TestModel.{ User, UserId }
import io.funcqrs.akka.backend.AkkaBackend
import io.funcqrs.config.api._
import org.scalatest._
import org.scalatest.concurrent.{ Eventually, ScalaFutures }
import org.scalatest.time.{ Seconds, Span }

import scala.concurrent.duration._

class AggregateManagerTest extends FlatSpecLike with Matchers with ScalaFutures with AkkaBackendSupport with Eventually {

  implicit val timeout = Timeout(500.millis)

  // very patient
  override implicit def patienceConfig: PatienceConfig =
    PatienceConfig(timeout = scaled(Span(5, Seconds)))

  override def configureBackend(backend: AkkaBackend): Unit = {
    backend.configure {
      aggregate(User.behavior)
    }
  }

  behavior of "An AggregateManager"

  it should "initialize a new actor when receiving a creational command" in {

    val userRef = aggregateRef[User].forId(UserId.generate())

    userRef.exists().futureValue shouldBe false
    userRef ! CreateUser("João Ninguém", 30)
    eventually {
      userRef.exists().futureValue shouldBe true
    }

    eventually {
      val user = userRef.state().futureValue
      user.name shouldBe "João Ninguém"
      user.age shouldBe 30
    }

    userRef ! ChangeName("Osvaldo Waldo")

    eventually {
      val user = userRef.state().futureValue
      user.name shouldBe "Osvaldo Waldo"
    }
  }

  it should "return false when enquiring for non-existent aggregate" in {

    val userRef = aggregateRef[User].forId(UserId.generate())

    userRef.exists().futureValue shouldBe false
  }

  it should "return true when enquiring for existent aggregate" in {

    val userRef = aggregateRef[User].forId(UserId.generate())

    userRef ! CreateUser("John Doe", 30)

    eventually {
      userRef.exists().futureValue shouldBe true
    }
  }

  it should "not accept a create command twice" in {

    val userRef = aggregateRef[User].forId(UserId.generate())

    userRef ! CreateUser("John Doe", 30)

    (userRef ? CreateUser("John Doe", 30)).failed.futureValue shouldBe a[MissingCommandHandlerException]
  }

  it should "reject commands if aggregate is 'deleted'" in {

    val userRef = aggregateRef[User].forId(UserId.generate())

    userRef ! CreateUser("John Doe", 30)

    userRef ! DeleteUser

    val error = (userRef ? CreateUser("John Doe", 30)).failed.futureValue
    error shouldBe a[IllegalArgumentException]
    error.getMessage contains "User is already deleted!"
  }

}
