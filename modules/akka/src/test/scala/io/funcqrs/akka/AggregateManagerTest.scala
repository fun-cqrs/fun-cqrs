package io.funcqrs.akka

import akka.actor.ActorSystem
import akka.actor.Status.Failure
import akka.testkit.{ ImplicitSender, TestKit }
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.funcqrs.backend.akka.api._
import io.funcqrs.{ AggregateId, CommandException, DomainCommand }
import io.funcqrs.akka.AggregateManager._
import io.funcqrs.akka.TestModel.UserProtocol._
import io.funcqrs.akka.TestModel.{ User, UserId }
import org.scalatest._
import scala.concurrent.duration._

class AggregateManagerTest(val actorSystem: ActorSystem) extends TestKit(actorSystem)
    with ImplicitSender
    with Matchers with FlatSpecLike with BeforeAndAfterAll {

  implicit val timeout = Timeout(500.millis)

  def this() = this(ActorSystem("test", ConfigFactory.load("application.conf")))

  implicit val backend = new AkkaBackend(actorSystem)(3.seconds)
  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  val aggregateManager = backend.actorOf {
    aggregate[User](User.behavior)
      .withAssignedId
  }

  behavior of "An AggregateManager"

  it should "initialize a new actor when receiving a creational command" in {

    val userId = UserId.generate()

    aggregateManager ! (userId, CreateUser("John Doe", 30))
    expectMsgPF(hint = "create event") {
      case (evt: UserCreated) :: _ =>
    }

    aggregateManager ! GetState(userId)
    expectMsgPF(hint = "check aggregate state") {
      case user: User =>
        user.name shouldBe "John Doe"
        user.age shouldBe 30
    }

    aggregateManager ! (userId, ChangeName("Osvaldo"))
    expectMsgPF(hint = "update name") {
      case (evt: NameChanged) :: _ =>
    }

    aggregateManager ! GetState(userId)
    expectMsgPF(hint = "check aggregate state - name changed") {
      case user: User =>
        user.name shouldBe "Osvaldo"
    }
  }

  it should "reject commands not defined in by its behavior" in {

    // no generated id so we can check error message
    val userId = UserId("test")

    val badCommand = new DomainCommand {
      override def toString: String = "BadCommand"
    }

    aggregateManager ! (userId, badCommand)
    expectMsgPF(hint = "sending bad command") {
      case Failure(exp: IllegalArgumentException) =>
        exp.getMessage shouldBe "Unknown message: (UserId(test),BadCommand)"
    }
  }

  it should "return false when enquiring for non-existent aggregate" in {

    val userId = UserId.generate()

    aggregateManager ! Exists(userId)
    expectMsgPF(hint = "checking existence of non-existent aggregate") {
      case false => // Ok
    }
  }

  it should "return true when enquiring for existent aggregate" in {

    val userId = UserId.generate()

    aggregateManager ! (userId, CreateUser("John Doe", 30))
    expectMsgPF(hint = "creating user") {
      case (evt: UserCreated) :: _ =>
    }

    aggregateManager ! Exists(userId)
    expectMsgPF(hint = "checking existence of existent aggregate") {
      case true => // Ok
    }
  }

  it should "not accept a create command twice" in {

    val userId = UserId.generate()
    aggregateManager ! (userId, CreateUser("John Doe", 30))
    expectMsgPF(hint = "creating user") {
      case (evt: UserCreated) :: _ =>
    }

    aggregateManager ! (userId, CreateUser("John Doe", 30))
    expectMsgPF(hint = "creating user") {
      case Failure(exp: CommandException) =>
        exp.getMessage contains "CreateUser(John Doe,30) for aggregate UserId"
    }
  }

  // FIXME: we can't type check on Id, need to investigate further
  ignore should "not accept AggregateIDs of another type" in {

    case class BadUserId(value: String) extends AggregateId

    aggregateManager ! (BadUserId("bad-id"), CreateUser("John Doe", 30))

    expectMsgPF(hint = "creating user") {
      case Failure(exp: IllegalArgumentException) =>
        exp.getMessage contains "Wrong aggregate id type "
    }
  }
}
