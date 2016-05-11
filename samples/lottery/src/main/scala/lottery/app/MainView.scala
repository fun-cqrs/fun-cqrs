package lottery.app

import io.funcqrs.config.api._
import akka.util.Timeout
import io.funcqrs.backend.QueryByTag
import lottery.domain.model.Lottery
import lottery.domain.service.{ LotteryViewProjection, LotteryViewRepo }
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ Await, Future }
import scala.util.{ Failure, Success }

object MainView extends App {

  val lotteryViewRepo = new LotteryViewRepo

  Config.backend.configure { // projection config - read model
    projection(
      query = QueryByTag(Lottery.tag),
      projection = new LotteryViewProjection(lotteryViewRepo),
      name = "LotteryViewProjection"
    )
  }
  Thread.sleep(2000)

  implicit val timeout = Timeout(3.seconds)
  // ---------------------------------------------
  // fetch read model
  val viewResult = lotteryViewRepo.find(Config.id)
  waitAndPrint(viewResult)

  Config.backend.actorSystem.terminate()

  def waitAndPrint[T](resultFut: Future[T]) = {
    Await.ready(resultFut, 3.seconds)
    resultFut.onComplete {
      case Success(res) => println(s" => result: $res")
      case Failure(ex) => println(s"FAILED: ${ex.getMessage}")
    }
  }

}
