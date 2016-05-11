package lottery.app

import akka.util.Timeout
import io.funcqrs.config.api._
import lottery.domain.model.Lottery
import lottery.domain.model.LotteryProtocol._

import scala.concurrent.duration._

object Main extends App {

  Config.backend
    .configure {
      // aggregate config - write model
      aggregate[Lottery](Lottery.behavior)
    }

  implicit val timeout = Timeout(3.seconds)

  val lotteryRef = Config.backend.aggregateRef[Lottery](Config.id)

  lotteryRef ! CreateLottery("Codemotion Demo")
  lotteryRef ! AddParticipant("John")
  lotteryRef ! AddParticipant("Paul")
  lotteryRef ! AddParticipant("Ringo")
  lotteryRef ! AddParticipant("George")
  lotteryRef ! Run

  Thread.sleep(1000)
  Config.backend.actorSystem.terminate()

}