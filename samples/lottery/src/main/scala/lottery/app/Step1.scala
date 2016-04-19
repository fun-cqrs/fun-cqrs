package lottery.app

import io.funcqrs.{ DomainEvent, HandleEvent, Projection }
import io.funcqrs.backend.QueryByTag
import io.funcqrs.config.api._
import lottery.domain.model.Lottery
import lottery.domain.model.LotteryProtocol._

import scala.concurrent.Future

object Step1 extends App with Common {

  class DummyProjection extends Projection {
    def handleEvent: HandleEvent = {
      case e: DomainEvent ⇒
        println(s"PROJECTION RECEIVED: $e")
        Future()
    }
  }

  backend.configure {
    projection(
      query = QueryByTag(Lottery.tag), // #<1>
      projection = new DummyProjection(), // #<2>
      name = "LotteryViewProjection" // #<3>
    ).withBackendOffsetPersistence() // #<4>
  }

  val result =
    for {
      createEvts <- lotteryRef ? CreateLottery("Demo") // #<2>
      johnEvts <- lotteryRef ? AddParticipant("John")
    } yield {
      createEvts ++ johnEvts
    }

  waitAndPrint(result)

  Thread.sleep(1000)
  backend.actorSystem.terminate()

}
