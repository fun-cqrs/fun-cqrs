package raffle.app

import raffle.domain.model._

import scala.util.{ Failure, Success }
object MainAkka extends App {

  val id = RaffleId.generate()

  val lotteryRef = AppContext.akkaBackend.aggregateRef[Raffle].forId(id)

  lotteryRef ! CreateRaffle

  // add participants
  lotteryRef ! AddParticipant("John")
  lotteryRef ! AddParticipant("Paul")
  lotteryRef ! AddParticipant("Ringo")
  lotteryRef ! AddParticipant("George")
  lotteryRef ! Run

  Thread.sleep(3000)

  // ---------------------------------------------
  // fetch read model
  val viewResult = AppContext.raffleViewRepo.find(id)

  viewResult match {
    case Success(res) => println(s" => result: $res")
    case Failure(ex)  => println(s"FAILED: ${ex.getMessage}")
  }

  AppContext.close

}
