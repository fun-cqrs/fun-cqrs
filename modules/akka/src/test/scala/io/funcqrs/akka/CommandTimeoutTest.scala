package io.funcqrs.akka

import java.util.UUID

import io.funcqrs.behavior._
import io.funcqrs.config.Api._
import io.funcqrs.{ AggregateId, AggregateLike, ProtocolLike }
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{ Seconds, Span }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CommandTimeoutTest extends FunSuite with Matchers with ScalaFutures with AkkaBackendSupport with BeforeAndAfter {

  import io.funcqrs.akka.PersonProtocol._

  // very patient
  override implicit def patienceConfig: PatienceConfig =
    PatienceConfig(timeout = scaled(Span(3, Seconds)))

  before {
    backend.configure {
      aggregate[Person](Person.behavior)
    }
  }

  test("timeout commands don't leave aggregate in busy state") {

    val personRef = backend.aggregateRef[Person](PersonId.generate)

    personRef ! MakePerson("Joe", 20)
    personRef ! ChangePersonsName("John")

    // we can only ask for the state after RegisterBirthday is applied
    whenReady(personRef ? RegisterBirthday) { _ =>
      // get person state after apply RegisterBirthday
      val person = personRef.state().futureValue

      // note that name won't be updated,
      // because ChangePersonsName must have timeout
      person.name shouldBe "Joe"

      // age is updated, which proves that although ChangePersonsName
      // was rejected with a timeout, it didn't 'lock' the aggregate
      person.age shouldBe 21
    }

  }

}
case class PersonId(value: String) extends AggregateId
object PersonId {

  def generate: PersonId =
    PersonId(UUID.randomUUID().toString)
}

object PersonProtocol extends ProtocolLike {
  sealed trait PersonCommand extends ProtocolCommand
  case class MakePerson(name: String, age: Int) extends PersonCommand
  case class ChangePersonsName(name: String) extends PersonCommand
  case object RegisterBirthday extends PersonCommand

  sealed trait PersonEvent extends ProtocolEvent
  case class PersonCreated(name: String, age: Int) extends PersonEvent
  case class PersonsNameUpdated(name: String) extends PersonEvent
  case class PersonsAgeUpdated(age: Int) extends PersonEvent
}

case class Person(id: PersonId, name: String, age: Int) extends AggregateLike {
  type Id       = PersonId
  type Protocol = PersonProtocol.type
}

object Person {

  import io.funcqrs.akka.PersonProtocol._
  def behavior(id: PersonId): Behavior[Person] =
    Behavior {
      actions[Person]
        .handleCommand { cmd: MakePerson =>
          PersonCreated(cmd.name, cmd.age)
        }
        .handleEvent { evt: PersonCreated =>
          Person(id, evt.name, evt.age)
        }
    } {
      case person =>
        actions[Person]
          .handleCommand {
            // change name takes a lot of time
            cmd: ChangePersonsName =>
              Future {
                Thread.sleep(3000) // it takes ages!!
                PersonsNameUpdated(cmd.name)
              }
          }
          .handleEvent { evt: PersonsNameUpdated =>
            person.copy(name = evt.name)
          }
          .handleCommand { cmd: RegisterBirthday.type =>
            PersonsAgeUpdated(person.age + 1)
          }
          .handleEvent { evt: PersonsAgeUpdated =>
            person.copy(age = evt.age)
          }
    }
}
