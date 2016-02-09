package lottery.app

import akka.util.Timeout
import io.funcqrs.akka.EventsSourceProvider
import io.funcqrs.akka.backend.{ AggregateActorRef, AkkaBackend }
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

  val backend = new AkkaBackend { // #<1>

    // override this val in order to use another ActorSystem
    // override val actorSystem: ActorSystem = ??? // #<2>


    def sourceProvider(query: Query): EventsSourceProvider = { // #<3>
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
      aggregate[Lottery](Lottery.behavior) // #<4>
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
  
  val lotteryRef = backend.aggregateRef[Lottery](id) // #<1>

  val result =
    for {
      // create a lottery
      createEvts <- lotteryRef ? CreateLottery("Demo") // #<2>

      // add participants #<3>
      johnEvts <- lotteryRef ? AddParticipant("John")
      paulEvts <- lotteryRef ? AddParticipant("Paul")
      ringoEvts <- lotteryRef ? AddParticipant("Ringo")
      georgeEvts <- lotteryRef ? AddParticipant("George")

      // run the lottery
      runEvts <- lotteryRef ? Run // #<4>

    } yield {
      // concatenate all events together
      createEvts ++ johnEvts ++ paulEvts ++ ringoEvts ++ georgeEvts ++ runEvts // #<5>
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