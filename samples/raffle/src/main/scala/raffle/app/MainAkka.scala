package raffle.app

import akka.actor.ActorSystem
import raffle.domain.model._

import scala.concurrent.Await
import scala.util.{ Failure, Success }
import scala.concurrent.duration._

object MainAkka extends App {

  val id = RaffleId.generate()

  val actorSys: ActorSystem = ActorSystem("FunCQRS")
  val backend               = AppContext.akkaBackend(actorSys)

  val lotteryRef = backend.aggregateRef[Raffle].forId(id)

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

  Await.ready(actorSys.terminate(), 5.seconds)

}
