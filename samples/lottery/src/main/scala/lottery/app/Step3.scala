package lottery.app

import io.funcqrs.backend.QueryByTag
import io.funcqrs.config.api._
import lottery.app.Step1.DummyProjection
import lottery.domain.model.Lottery
import lottery.domain.model.LotteryProtocol._
import lottery.domain.service.{ LotteryViewProjection, LotteryViewRepo }

import scala.concurrent.ExecutionContext.Implicits.global

object Step3 extends App with Common {

  // here we miss the paul event
  backend.configure {
    projection(
      query = QueryByTag(Lottery.tag), // #<1>
      projection = new DummyProjection(), // #<2>
      name = "LotteryViewProjection" // #<3>
    ).withBackendOffsetPersistence() // #<4>
  }

  Thread.sleep(5000)
  backend.actorSystem.terminate()

}
