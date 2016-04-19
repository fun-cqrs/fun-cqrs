package lottery.app

import lottery.domain.model.LotteryProtocol._

object Step2 extends App with Common {

  val result =
    for {
      paulEvts <- lotteryRef ? AddParticipant("Paul")
      ringoEvts <- lotteryRef ? AddParticipant("Ringo")
      georgeEvts <- lotteryRef ? AddParticipant("George")
    } yield {
      paulEvts ++ ringoEvts ++ georgeEvts
    }

  waitAndPrint(result)

  Thread.sleep(1000)
  backend.actorSystem.terminate()

}
