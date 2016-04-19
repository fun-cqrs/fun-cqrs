package lottery.app

import akka.actor.ActorSystem
import akka.util.Timeout
import io.funcqrs.akka.EventsSourceProvider
import io.funcqrs.akka.backend.AkkaBackend
import io.funcqrs.backend.{ Query, QueryByTag }
import io.funcqrs.config.Api._
import lottery.domain.model.{ Lottery, LotteryId }
import lottery.domain.service.JDBCTaggedEventsSource

import scala.concurrent.{ Await, Future }
import scala.util.{ Failure, Success }
import scala.concurrent.duration._

trait Common {

  val backend = new AkkaBackend {
    // #<1>
    val actorSystem: ActorSystem = ActorSystem("FunCQRS")
    // #<2>
    def sourceProvider(query: Query): EventsSourceProvider = {
      // #<3>
      query match {
        case QueryByTag(tag) => new JDBCTaggedEventsSource(tag)
      }
    }
  }

  backend
    .configure {
      // aggregate config - write model
      aggregate[Lottery](Lottery.behavior) // #<4>
    }

  implicit val timeout = Timeout(3.seconds)
  implicit val ec = scala.concurrent.ExecutionContext.Implicits.global

  val id = LotteryId("e61c422c-a4d3-4884-81ef-8fa46a6c20a0")

  val lotteryRef = backend.aggregateRef[Lottery](id) //#<1>

  def waitAndPrint[T](resultFut: Future[T]) = {
    Await.ready(resultFut, 3.seconds)
    resultFut.onComplete {
      case Success(res) => println(s" => result: $res")
      case Failure(ex) => println(s"FAILED: ${ex.getMessage}")
    }
  }

}
