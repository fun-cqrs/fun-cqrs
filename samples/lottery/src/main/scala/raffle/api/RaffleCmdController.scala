package raffle.api

import akka.actor.ActorRef
import com.softwaremill.macwire._
import play.api.libs.json.{JsResult, JsValue}
import play.api.mvc.RequestHeader
import raffle.api.routes.{RaffleQueryController => ReverseQueryCtrl}
import raffle.domain.model.RaffleProtocol.RaffleCommand
import raffle.domain.model.{Raffle, RaffleId, RaffleProtocol}

class RaffleCmdController(val aggregateManager: ActorRef @@ Raffle.type)
  extends CommandController with AssignedId {

  type AggregateType = Raffle

  def aggregateId(id: String): RaffleId = RaffleId(id)

  def toCommand(jsValue: JsValue): JsResult[RaffleCommand] = RaffleProtocol.commandsFormat.reads(jsValue)

  def toLocation(id: String)(implicit request: RequestHeader): String = {
    ReverseQueryCtrl.get(id).absoluteURL()
  }

  def toAggregateId(id: String): RaffleId = RaffleId.fromString(id)

}