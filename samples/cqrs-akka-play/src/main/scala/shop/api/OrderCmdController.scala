package shop.api

import akka.actor.ActorRef
import com.softwaremill.macwire._
import play.api.libs.json.{ JsResult, JsValue }
import play.api.mvc.RequestHeader
import shop.api.routes.{ OrderQueryController => ReverseQueryCtrl }
import shop.domain.model.OrderProtocol.OrderCommand
import shop.domain.model.{ Order, OrderNumber, OrderProtocol }

class OrderCmdController(val aggregateManager: ActorRef @@ Order.type)
    extends CommandController with AssignedId {

  type AggregateType = Order

  def aggregateId(id: String): OrderNumber = OrderNumber(id)

  def toCommand(jsValue: JsValue): JsResult[OrderCommand] =
    OrderProtocol.commandFormats.reads(jsValue)

  def toAggregateId(id: String): OrderNumber = OrderNumber.fromString(id)

  def toLocation(id: String)(implicit request: RequestHeader): String = {
    ReverseQueryCtrl.get(id).absoluteURL()
  }
}
