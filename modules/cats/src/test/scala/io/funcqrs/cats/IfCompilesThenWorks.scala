package io.funcqrs.cats

import cats.Functor
import cats.data.Validated.{Invalid, Valid}
import cats.data.{Validated, ValidatedNel}
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

  def validate(v: Int): Validated[ValidationError, Int] = v match {
    case 2 => Invalid(TwoIsIllegal)
    case 13 => Invalid(ThirteenIsIllegal)
    case n => Valid(n)
  }

  test("validatedId OneEvent and ManyEvents should be accepted (compiled)") {

    object Test1 extends TestTypes

    import cats.implicits._
    import validatedId._

    Test1.actions
      .commandHandler {
        OneEvent {
          case AddValue(x) => validate(x).map(ValueAdded).toValidatedNel
        }
      }
      .commandHandler {
        ManyEvents {
          case AddValues(vs) =>
            val res: ValidatedNel[ValidationError, List[TestEvent]] =
              vs.toList.map(validate)
                .map(_.map(ValueAdded))
                .map(_.toValidatedNel)
                .sequenceU
            res
        }
      }
  }

  test("validated OneEvent and ManyEvents should be accepted (compiled)") {
    object Test1 extends TestTypes

    import cats.implicits._
    import scala.concurrent.ExecutionContext.Implicits.global
    
    import scala.collection.immutable.Seq

    implicit val futureFunctor: Functor[Future] = new Functor[Future] {
      def map[A,B](fa: Future[A])(f: A => B) = fa map f
    }
    
    new ValidatedCommandHandlers[ValidationError, Option] {
      Test1.actions
        .commandHandler {
          OneEvent[TestCommand, TestEvent] {
            case AddValue(x) => validate(x)
              .map(v => Some[TestEvent](ValueAdded(v)))
              .toValidatedNel
          }
        }
        .commandHandler {
          ManyEvents {
            case AddValues(vs) =>
              val es: ValidatedNel[ValidationError, Seq[Option[TestEvent]]] =
                vs.toList.map(validate)
                  .map(_.map(v => Some(ValueAdded(v))))
                  .map(_.toValidatedNel)
                  .sequenceU
              es.map(x => Some(x.flatten).filter(_.nonEmpty))
          }
        }
    }
  }

}