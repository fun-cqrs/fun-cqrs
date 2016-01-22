package io.funcqrs.akka

import akka.actor.ActorSystem
import akka.actor.Status.Failure
import akka.testkit.{ ImplicitSender, TestKit }
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.funcqrs.backend.akka.api._
import io.funcqrs.{ CommandMsg, AggregateId, CommandException, DomainCommand }
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

    type Aggregate = User

    val userId = UserId.generate()

    aggregateManager ! CommandMsg(userId, CreateUser("John Doe", 30))
    expectMsgPF(hint = "create event") {
      case (evt: UserCreated) :: _ =>
    }

    aggregateManager ! GetState(userId)
    expectMsgPF(hint = "check aggregate state") {
      case user: User =>
        user.name shouldBe "John Doe"
        user.age shouldBe 30
    }

    aggregateManager ! CommandMsg(userId, ChangeName("Osvaldo"))
    expectMsgPF(hint = "update name") {
      case (evt: NameChanged) :: _ =>
    }

    aggregateManager ! GetState(userId)
    expectMsgPF(hint = "check aggregate state - name changed") {
      case user: User =>
        user.name shouldBe "Osvaldo"
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

    aggregateManager ! CommandMsg(userId, CreateUser("John Doe", 30))
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
    aggregateManager ! CommandMsg(userId, CreateUser("John Doe", 30))
    expectMsgPF(hint = "creating user") {
      case (evt: UserCreated) :: _ =>
    }

    aggregateManager ! CommandMsg(userId, CreateUser("John Doe", 30))
    expectMsgPF(hint = "creating user") {
      case Failure(exp: CommandException) =>
        exp.getMessage contains "CreateUser(John Doe,30) for aggregate UserId"
    }
  }

  it should "reject commands is aggregate is 'deleted'" in {

    // no generated id so we can check error message
    val userId = UserId.generate()
    aggregateManager ! CommandMsg(userId, CreateUser("John Doe", 30))
    expectMsgPF(hint = "creating user") {
      case (evt: UserCreated) :: _ =>
    }

    aggregateManager ! CommandMsg(userId, DeleteUser)
    expectMsgPF(hint = "sending delete command") {
      case (evt: UserDeleted) :: _ => //ok
    }

    aggregateManager ! CommandMsg(userId, ChangeName("Osvaldo"))
    expectMsgPF(hint = "update name") {
      case Failure(exp: IllegalArgumentException) =>
        exp.getMessage contains "User is already deleted!"
    }
  }

  it should "reject commands not defined in by its behavior" in {

    // no generated id so we can check error message
    val userId = UserId("test")

    val badCommand = new DomainCommand {
      override def toString: String = "BadCommand"
    }

    aggregateManager ! CommandMsg(userId, badCommand)
    expectMsgPF(hint = "sending bad command") {
      case Failure(exp: IllegalArgumentException) =>
        exp.getMessage shouldBe "Unknown message: CommandMsg(UserId(test),BadCommand)"
    }
  }

  // FIXME: we can't type check on Id, need to investigate further
  ignore should "not accept AggregateIDs of another type" in {

    case class BadUserId(value: String) extends AggregateId

    aggregateManager ! CommandMsg(BadUserId("bad-id"), CreateUser("John Doe", 30))

    expectMsgPF(hint = "creating user") {
      case Failure(exp: IllegalArgumentException) =>
        exp.getMessage contains "Wrong aggregate id type "
    }
  }
}
