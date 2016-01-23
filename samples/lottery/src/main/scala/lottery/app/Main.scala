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

object Main extends App {

  // tag::lottery-actor[]
  implicit val timeout = Timeout(3.seconds)
  val system = ActorSystem("FunCQRS")
  implicit lazy val backend = new AkkaBackend(system)

  // ---------------------------------------------
  // aggregate config - write model
  val lotteryService =
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

  val id = LotteryId.generate()
  // ---------------------------------------------
  // create aggregate
  val result =
    for {
      _ <- lotteryService.newInstance(id, CreateLottery("ScalaX"))
      _ <- lotteryService.update(id)(AddParticipant("John"))
      _ <- lotteryService.update(id)(AddParticipant("Paul"))
      _ <- lotteryService.update(id)(AddParticipant("Joe"))
      res <- lotteryService.update(id)(Run)
    } yield res

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