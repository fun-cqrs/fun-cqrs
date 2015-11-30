package shop.api

import io.funcqrs.akka.AggregateService
import play.api.libs.json.JsValue
import play.api.mvc.RequestHeader
import shop.api.routes.{ OrderQueryController => ReverseQueryCtrl }
import shop.domain.model.{ Order, OrderNumber, OrderProtocol }

class OrderCmdController(val aggregateService: AggregateService[Order])
    extends CommandController with AssignedIdCmdController {

  type Aggregate = Order

  def aggregateId(id: String): OrderNumber = OrderNumber(id)

  def toCommand(jsValue: JsValue) =
    OrderProtocol.commandFormats.reads(jsValue)

  def toAggregateId(id: String): OrderNumber = OrderNumber.fromString(id)

  def toLocation(id: String)(implicit request: RequestHeader): String = {
    ReverseQueryCtrl.get(id).absoluteURL()
  }
}
