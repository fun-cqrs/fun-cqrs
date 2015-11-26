package shop.api

import akka.actor.ActorRef
import com.softwaremill.macwire._
import io.funcqrs.DomainCommand
import play.api.libs.json.{ JsResult, JsValue }
import play.api.mvc.RequestHeader
import shop.api.routes.{ CustomerQueryController => ReverseQueryCtrl }
import shop.domain.model.{ Customer, CustomerId, CustomerProtocol }

class CustomerCmdController(val aggregateManager: ActorRef @@ Customer.type)
    extends CommandController with AssignedId {

  type AggregateType = Customer

  def aggregateId(id: String): CustomerId = CustomerId(id)

  def toCommand(jsValue: JsValue): JsResult[DomainCommand] =
    CustomerProtocol.commandsFormat.reads(jsValue)

  def toAggregateId(id: String): CustomerId = CustomerId.fromString(id)

  def toLocation(id: String)(implicit request: RequestHeader): String = {
    ReverseQueryCtrl.get(id).absoluteURL()
  }
}
