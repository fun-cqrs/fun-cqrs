package raffle.app

import raffle.domain.model._
import raffle.domain.model.{ Raffle, RaffleId }

import scala.util.{ Failure, Success }

object MainInMemory extends App {

  val id = RaffleId.generate()

  val lotteryRef = AppContext.inMemoryBackend.aggregateRef[Raffle].forId(id)

  lotteryRef ! CreateRaffle

  // add participants
  lotteryRef ! AddParticipant("John")
  lotteryRef ! AddParticipant("Paul")
  lotteryRef ! AddParticipant("Ringo")
  lotteryRef ! AddParticipant("George")
  lotteryRef ! Run

  // ---------------------------------------------
  // fetch read model
  val viewResult = AppContext.raffleViewRepo.find(id)

  viewResult match {
    case Success(res) => println(s" => result: $res")
    case Failure(ex)  => println(s"FAILED: ${ex.getMessage}")
  }

}
