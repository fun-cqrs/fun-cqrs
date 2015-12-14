package io.funcqrs.dsl

import java.util.UUID

import io.funcqrs._
import io.funcqrs.interpreters.Monads.Monad
import io.funcqrs.interpreters.{ Interpreter, AsyncInterpreter, IdentityInterpreter, TryInterpreter }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ Await, Future }
import scala.language.higherKinds
import scala.util.Try

object BindingMain extends App {

  import BindingDsl.api._

  object FooProtocol extends ProtocolLike {

    trait FooCmd extends ProtocolCommand
    case class CreateFoo(name: String) extends ProtocolCommand
    case class UpdateFoo(name: String) extends ProtocolCommand
    case class UpdateBaz(name: String) extends ProtocolCommand

    trait FooEvent extends ProtocolEvent {

      def name: String

      val id = EventId()
      val commandId = CommandId()
    }

    case class FooEvt(name: String) extends FooEvent

    case class BazEvt(name: String) extends FooEvent

  }

  case class FooId(value: String) extends AggregateID

  object FooId {

    def apply(): FooId = FooId(UUID.randomUUID.toString)
  }

  case class Foo(name: String, id: FooId = FooId()) extends AggregateLike {

    type Id = FooId
    type Protocol = FooProtocol.type
  }

  def show[A <: AggregateLike](binding: Binding[A]) = {
    println(binding)
  }

  import FooProtocol._

  val spec = describe[Foo]
    .whenCreating {

      handler { cmd: CreateFoo => FooEvt(cmd.name) }
        .listener { evt => Foo(evt.name) }

    } whenUpdating { foo =>

      handler
        .manyEvents { cmd: UpdateFoo => List(FooEvt(cmd.name), BazEvt(cmd.name)) }
        .listener {
          case evt: FooEvt => foo.copy(name = evt.name)
          case evt: BazEvt => foo.copy(name = evt.name)
        }

    } whenUpdating { foo =>

      handler
        .manyEvents { cmd: UpdateFoo => List(FooEvt(cmd.name), FooEvt(cmd.name)) }
        .listener { case evt => foo.copy(name = evt.name) }

    } whenUpdating { foo =>

      tryHandler { cmd: UpdateFoo => Try(BazEvt(cmd.name)) }
        .listener { evt => foo.copy(name = evt.name) }

    } whenUpdating { foo =>

      asyncHandler { cmd: UpdateBaz => Future { BazEvt(cmd.name) } }
        .listener { evt => foo.copy(name = evt.name) }

    }

  def run[F[_]](interpreter: Interpreter[Foo, F], label: String)(implicit monad: Monad[F]): F[_] = {

    println("------------------------------------")
    println("==> " + label)
    val eventsF = interpreter.handleCommand(CreateFoo("abc"))
    println(eventsF)

    val fooM = monad.map(eventsF) { evts => interpreter.onEvent(evts.head) }
    println(fooM)

    val updateEvents1 = monad.flatMap(fooM) { agg => interpreter.handleCommand(agg, UpdateFoo("def")) }
    println(updateEvents1)

    val fooM2 =
      monad.flatMap(fooM) { foo =>
        monad.map(updateEvents1) { evts =>
          evts.foldLeft(foo) { case (agg, evt) => interpreter.onEvent(agg, evt) }
        }
      }
    println(fooM2)

    val updateEvents2 = monad.flatMap(fooM2) { agg => interpreter.handleCommand(agg, UpdateBaz("baz")) }

    val fooM3 =
      monad.flatMap(fooM2) { foo =>
        monad.map(updateEvents2) { evts =>
          evts.foldLeft(foo) { case (agg, evt) => interpreter.onEvent(agg, evt) }
        }
      }

    println(fooM3)

    println("====================================")
    println()
    fooM3
  }

  run(new IdentityInterpreter(spec), "Identity")
  run(new TryInterpreter(spec), "Try")
  import scala.concurrent.duration._
  val result = Await.result(run(new AsyncInterpreter(spec), "Async (Future)"), 5.seconds)
  println(s"async result = $result")
}
