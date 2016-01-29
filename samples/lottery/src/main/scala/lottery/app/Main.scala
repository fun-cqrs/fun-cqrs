package lottery.app

import akka.util.Timeout
import io.funcqrs.akka.EventsSourceProvider
import io.funcqrs.akka.backend.AkkaBackend
import io.funcqrs.backend.{ Query, QueryByTag }
import io.funcqrs.config.api._
import lottery.domain.model.{ Lottery, LotteryId }
import lottery.domain.model.LotteryProtocol._
import lottery.domain.service.{ LevelDbTaggedEventsSource, LotteryViewProjection, LotteryViewRepo }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

object Main extends App {

  // tag::lottery-actor[]

  val backend = new AkkaBackend {
    def sourceProvider(query: Query): EventsSourceProvider = {
      query match {
        case QueryByTag(tag) => new LevelDbTaggedEventsSource(tag)
      }
    }
  }

  val lotteryViewRepo = new LotteryViewRepo

  backend
    // ---------------------------------------------
    // aggregate config - write model
    .configure {
      aggregate[Lottery](Lottery.behavior)
    }
    // end::lottery-actor[]

    // ---------------------------------------------
    // projection config - read model
    .configure {
      projection(
        query = QueryByTag(Lottery.tag),
        projection = new LotteryViewProjection(lotteryViewRepo),
        name = "LotteryViewProjection"
      )
    }

  // tag::lottery-run[]
  implicit val timeout = Timeout(3.seconds)

  val id = LotteryId.generate()
  val aggregateRef = backend.aggregateRef[Lottery](id)

  val result =
    for {
      // create a lottery
      createEvts <- aggregateRef ? CreateLottery("Demo") // #<1>

      // add participants #<2>
      johnEvts <- aggregateRef ? AddParticipant("John")
      paulEvts <- aggregateRef ? AddParticipant("Paul")
      ringoEvts <- aggregateRef ? AddParticipant("Ringo")
      georgeEvts <- aggregateRef ? AddParticipant("George")

      // run the lottery
      runEvts <- aggregateRef ? Run // #<3>

    } yield {
      // concatenate all events together
      createEvts ++ johnEvts ++ paulEvts ++ ringoEvts ++ georgeEvts ++ runEvts // #<4>
    }
  // end::lottery-run[]

  waitAndPrint(result)
  Thread.sleep(5000)

  // ---------------------------------------------
  // fetch read model
  val viewResult = lotteryViewRepo.find(id)
  waitAndPrint(viewResult)

  Thread.sleep(1000)
  backend.actorSystem.terminate()

  def waitAndPrint[T](resultFut: Future[T]) = {
    Await.ready(resultFut, 3.seconds)
    resultFut.onComplete {
      case Success(res) => println(s" => result: $res")
      case Failure(ex) => println(s"FAILED: ${ex.getMessage}")
    }
  }
}