package shop.api

import io.funcqrs.akka.AggregateService
import play.api.libs.json.{ JsResult, JsValue }
import play.api.mvc.RequestHeader
import shop.api.routes.{ CustomerQueryController => ReverseQueryCtrl }
import shop.domain.model.{ Customer, CustomerId, CustomerProtocol }

class CustomerCmdController(val aggregateService: AggregateService[Customer])
    extends CommandController with AssignedIdCmdController {

  type Aggregate = Customer

  def aggregateId(id: String): CustomerId = CustomerId(id)

  def toCommand(jsValue: JsValue): JsResult[aggregateService.Command] =
    CustomerProtocol.commandsFormat.reads(jsValue)

  def toAggregateId(id: String): CustomerId = CustomerId.fromString(id)

  def toLocation(id: String)(implicit request: RequestHeader): String = {
    ReverseQueryCtrl.get(id).absoluteURL()
  }
}
