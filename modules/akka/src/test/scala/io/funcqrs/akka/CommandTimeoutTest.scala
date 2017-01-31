package io.funcqrs.akka

import java.util.UUID

import io.funcqrs.AggregateId
import io.funcqrs.akka.backend.AkkaBackend
import io.funcqrs.behavior._
import io.funcqrs.config.Api._
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{ Seconds, Span }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CommandTimeoutTest extends FunSuite with Matchers with ScalaFutures with AkkaBackendSupport with BeforeAndAfter {

  // very patient
  override implicit def patienceConfig: PatienceConfig =
    PatienceConfig(timeout = scaled(Span(5, Seconds)))

  override def configureBackend(backend: AkkaBackend): Unit = {
    backend.configure {
      aggregate(Person.behavior)
    }
  }

  test("timeout commands don't leave aggregate in busy state") {

    val personRef = aggregateRef[Person].forId(PersonId.generate)

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

case class Person(id: PersonId, name: String, age: Int)
case class PersonId(value: String) extends AggregateId
object PersonId {
  def generate: PersonId = PersonId(UUID.randomUUID().toString)
}

sealed trait PersonCommand
final case class MakePerson(name: String, age: Int) extends PersonCommand
final case class ChangePersonsName(name: String) extends PersonCommand
case object RegisterBirthday extends PersonCommand

sealed trait PersonEvent
final case class PersonCreated(name: String, age: Int) extends PersonEvent
final case class PersonsNameUpdated(name: String) extends PersonEvent
final case class PersonsAgeUpdated(age: Int) extends PersonEvent

object Person extends Types[Person] {

  type Id      = PersonId
  type Command = PersonCommand
  type Event   = PersonEvent

  def behavior(id: PersonId) =
    Behavior.construct {
      actions
        .commandHandler {
          OneEvent {
            case MakePerson(name, age) => PersonCreated(name, age)
          }
        }
        .eventHandler {
          case PersonCreated(name, age) => Person(id, name, age)
        }
    } andThen {
      case person =>
        actions
          .commandHandler {
            EventuallyOneEvent {
              case ChangePersonsName(name) =>
                Future {
                  Thread.sleep(3000) // it takes ages!!
                  PersonsNameUpdated(name)
                }
            }
          }
          .eventHandler {
            case PersonsNameUpdated(name) => person.copy(name = name)
          }
          .commandHandler {
            OneEvent {
              case RegisterBirthday => PersonsAgeUpdated(person.age + 1)
            }
          }
          .eventHandler {
            case PersonsAgeUpdated(age) => person.copy(age = age)
          }
    }
}
