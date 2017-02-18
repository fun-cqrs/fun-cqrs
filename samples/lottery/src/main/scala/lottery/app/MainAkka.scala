package lottery.app

import lottery.domain.model._

import scala.util.{ Failure, Success }
object MainAkka extends App {

  val id = LotteryId.generate()

  val lotteryRef = AppContext.akkaBackend.aggregateRef[Lottery].forId(id)

  lotteryRef ! CreateLottery

  // add participants
  lotteryRef ! AddParticipant("John")
  lotteryRef ! AddParticipant("Paul")
  lotteryRef ! AddParticipant("Ringo")
  lotteryRef ! AddParticipant("George")
  lotteryRef ! Run

  Thread.sleep(3000)

  // ---------------------------------------------
  // fetch read model
  val viewResult = AppContext.lotteryViewRepo.find(id)

  viewResult match {
    case Success(res) => println(s" => result: $res")
    case Failure(ex)  => println(s"FAILED: ${ex.getMessage}")
  }

  AppContext.close

}
