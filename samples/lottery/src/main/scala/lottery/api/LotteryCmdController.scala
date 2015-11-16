package lottery.api

import akka.actor.ActorRef
import com.softwaremill.macwire._
import play.api.libs.json.{ JsResult, JsValue }
import play.api.mvc.RequestHeader
import lottery.api.routes.{ LotteryQueryController => ReverseQueryCtrl }
import lottery.domain.model.LotteryProtocol.LotteryCommand
import lottery.domain.model.{ Lottery, LotteryId, LotteryProtocol }

class LotteryCmdController(val aggregateManager: ActorRef @@ Lottery.type)
    extends CommandController with AssignedId {

  type AggregateType = Lottery

  def aggregateId(id: String): LotteryId = LotteryId(id)

  def toCommand(jsValue: JsValue): JsResult[LotteryCommand] = LotteryProtocol.commandsFormat.reads(jsValue)

  def toLocation(id: String)(implicit request: RequestHeader): String = {
    ReverseQueryCtrl.get(id).absoluteURL()
  }

  def toAggregateId(id: String): LotteryId = LotteryId.fromString(id)

}