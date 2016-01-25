package lottery.app

import akka.actor.ActorSystem
import lottery.domain.model.Lottery
import lottery.domain.model.LotteryId
import lottery.domain.model.LotteryProtocol._
import lottery.domain.service.{ LevelDbTaggedEventsSource, LotteryViewRepo, LotteryViewProjection }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.util.Timeout
import java.util.UUID
import io.funcqrs.backend.akka.api._
import scala.util.{ Failure, Success }
import io.funcqrs.backend.akka.AggregateService

object Main extends App {

  // tag::lottery-actor[]
  val system = ActorSystem("FunCQRS")

  implicit val timeout = Timeout(3.seconds)
  implicit val backend = new AkkaBackend(system)

  // ---------------------------------------------
  // aggregate config - write model
  val lotteryService: AggregateService[Lottery] =
    configure {
      aggregate[Lottery](Lottery.behavior)
    }
  // end::lottery-actor[]

  // ---------------------------------------------
  // projection config - read model
  val lotteryViewRepo = new LotteryViewRepo

  configure {
    projection(
      sourceProvider = new LevelDbTaggedEventsSource(Lottery.tag),
      projection = new LotteryViewProjection(lotteryViewRepo),
      name = "LotteryViewProjection"
    )
  }

  // tag::lottery-run[]
  val id = LotteryId.generate()

  val result = //#<4>
    for {
      // create a lottery
      createEvts <- lotteryService.newInstance(id, CreateLottery("Demo")) // #<1>

      // add participants
      johnEvts <- lotteryService.update(id)(AddParticipant("John")) //#<2>
      paulEvts <- lotteryService.update(id)(AddParticipant("Paul"))
      ringoEvts <- lotteryService.update(id)(AddParticipant("Ringo"))
      georgeEvts <- lotteryService.update(id)(AddParticipant("George"))

      // run the lottery
      runEvts <- lotteryService.update(id)(Run) // #<3>

    } yield {
      // concatenate all events together
      createEvts ++ johnEvts ++ paulEvts ++ ringoEvts ++ georgeEvts ++ runEvts
    }
  // end::lottery-run[]

  waitAndPrint(result)
  Thread.sleep(5000)

  // ---------------------------------------------
  // fetch read model
  val viewResult = lotteryViewRepo.find(id)
  waitAndPrint(viewResult)

  Thread.sleep(1000)
  system.terminate()

  def waitAndPrint[T](resultFut: Future[T]) = {
    Await.ready(resultFut, 3.seconds)
    resultFut.onComplete {
      case Success(res) => println(s" => result: $res")
      case Failure(ex) => println(s"FAILED: ${ex.getMessage}")
    }
  }
}