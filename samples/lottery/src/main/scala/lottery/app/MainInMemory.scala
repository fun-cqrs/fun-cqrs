package lottery.app

import lottery.domain.model._
import lottery.domain.model.{ Lottery, LotteryId }

import scala.util.{ Failure, Success }

object MainInMemory extends App {

  val id = LotteryId.generate()

  val lotteryRef =
    AppContext.inMemoryBackend
      .aggregateRef[Lottery]
      .forId(id)

  lotteryRef ! CreateLottery

  // add participants
  lotteryRef ! AddParticipant("John")
  lotteryRef ! AddParticipant("Paul")
  lotteryRef ! AddParticipant("Ringo")
  lotteryRef ! AddParticipant("George")
  lotteryRef ! Run

  // ---------------------------------------------
  // fetch read model
  val viewResult = AppContext.lotteryViewRepo.find(id)

  viewResult match {
    case Success(res) => println(s" => result: $res")
    case Failure(ex)  => println(s"FAILED: ${ex.getMessage}")
  }

}
