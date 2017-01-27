package io.funcqrs.akka

import akka.actor.ActorSystem
import akka.actor.Status.Failure
import akka.testkit.{ ImplicitSender, TestKit }
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.funcqrs.akka.AggregateManager._
import io.funcqrs.akka.TestModel.{ User, UserId }
import io.funcqrs.akka.backend.AkkaBackend
import io.funcqrs.backend.Query
import io.funcqrs.config.api._
import io.funcqrs.{ AggregateId, DomainCommand, MissingCommandHandlerException }
import org.scalatest._
import org.scalatest.concurrent.{ Eventually, ScalaFutures }
import org.scalatest.exceptions.TestFailedException
import org.scalatest.time.{ Seconds, Span }

import scala.concurrent.Await
import scala.concurrent.duration._

class AggregateManagerTest extends FlatSpecLike with Matchers with ScalaFutures with AkkaBackendSupport with Eventually {

  implicit val timeout = Timeout(500.millis)

  // very patient
  override implicit def patienceConfig: PatienceConfig =
    PatienceConfig(timeout = scaled(Span(3, Seconds)))

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    backend.configure {
      aggregate(User.behavior)
    }
  }

  behavior of "An AggregateManager"

  it should "initialize a new actor when receiving a creational command" in {

    val userRef = backend.aggregateRef[User].forId(UserId.generate())

    userRef.exists().futureValue shouldBe false
    userRef ! CreateUser("John Doe", 30)
    eventually {
      userRef.exists().futureValue shouldBe true
    }

    eventually {
      val user = userRef.state().futureValue
      user.name shouldBe "John Doe"
      user.age shouldBe 30
    }

    userRef ! ChangeName("Osvaldo")

    eventually {
      val user = userRef.state().futureValue
      user.name shouldBe "Osvaldo"
    }
  }

  it should "return false when enquiring for non-existent aggregate" in {

    val userRef = backend.aggregateRef[User].forId(UserId.generate())

    userRef.exists().futureValue shouldBe false
  }

  it should "return true when enquiring for existent aggregate" in {

    val userRef = backend.aggregateRef[User].forId(UserId.generate())

    userRef ! CreateUser("John Doe", 30)

    eventually {
      userRef.exists().futureValue shouldBe true
    }
  }

  it should "not accept a create command twice" in {

    val userRef = backend.aggregateRef[User].forId(UserId.generate())

    userRef ! CreateUser("John Doe", 30)

    (userRef ? CreateUser("John Doe", 30)).failed.futureValue shouldBe a[MissingCommandHandlerException]
  }

  it should "reject commands if aggregate is 'deleted'" in {

    val userRef = backend.aggregateRef[User].forId(UserId.generate())

    userRef ! CreateUser("John Doe", 30)

    userRef ! DeleteUser

    val error = (userRef ? CreateUser("John Doe", 30)).failed.futureValue
    error shouldBe a[IllegalArgumentException]
    error.getMessage contains "User is already deleted!"
  }

}
