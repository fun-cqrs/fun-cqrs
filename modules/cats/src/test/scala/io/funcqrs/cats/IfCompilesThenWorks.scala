package io.funcqrs.cats

import cats.Functor
import cats.data.ValidatedNel
import io.funcqrs.AggregateId
import io.funcqrs.behavior.Types
import org.scalatest.{FunSuite, Matchers}

import scala.concurrent.Future

trait Test {}

sealed trait TestCommand

case class AddValue(v: Int) extends TestCommand

case class AddValues(v: Seq[Int]) extends TestCommand

sealed trait TestEvent

case class ValueAdded(v: Int) extends TestEvent

case class TestId(value: String) extends AggregateId

trait TestTypes extends Types[Test] {
  type Id = TestId
  type Command = TestCommand
  type Event = TestEvent
}

case object ThirteenIsIllegal extends ValidationError

case object TwoIsIllegal extends ValidationError


class ActionsTest extends FunSuite with Matchers {

  import cats.syntax.validated._

  def validate(v: Int): ValidatedNel[ValidationError, Int] = v match {
    case 2 => TwoIsIllegal.invalidNel
    case 13 => ThirteenIsIllegal.invalidNel
    case n => n.validNel
  }

  test("validatedId OneEvent and ManyEvents should be accepted (compiled)") {

    object Test1 extends TestTypes

    import cats.implicits._
    import validatedId._

    Test1.actions
      .commandHandler {
        OneEvent {
          case AddValue(x) => validate(x).map(ValueAdded)
        }
      }
      .commandHandler {
        ManyEvents {
          case AddValues(vs) =>
            vs.toList.traverseU(validate(_).map(ValueAdded))
        }
      }
  }

  test("validated OneEvent and ManyEvents should be accepted (compiled)") {
    object Test1 extends TestTypes

    import cats.implicits._
    import scala.concurrent.ExecutionContext.Implicits.global

    import scala.collection.immutable.Seq

    new ValidatedCommandHandlers[ValidationError, Option] {
      Test1.actions
        .commandHandler {
          OneEvent[TestCommand, TestEvent] {
            case AddValue(x) => validate(x)
              .map(v => Some[TestEvent](ValueAdded(v)))
          }
        }
        .commandHandler {
          ManyEvents {
            case AddValues(vs) =>
              val es: ValidatedNel[ValidationError, Seq[Option[TestEvent]]] =
                vs.toList.traverseU(validate(_).map(v => Some(ValueAdded(v))))
              es.map(x => Some(x.flatten).filter(_.nonEmpty))
          }
        }
    }
  }

}