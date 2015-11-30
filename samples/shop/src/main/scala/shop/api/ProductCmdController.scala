package shop.api

import io.funcqrs.akka.AggregateService
import play.api.libs.json.JsValue
import play.api.mvc.RequestHeader
import shop.api.routes.{ ProductQueryController => ReverseQueryCtrl }
import shop.domain.model.{ Product, ProductNumber, ProductProtocol }

import scala.language.postfixOps

class ProductCmdController(val aggregateService: AggregateService[Product])
    extends CommandController with AssignedIdCmdController {

  type Aggregate = Product
  def aggregateId(id: String): ProductNumber = ProductNumber(id)

  def toCommand(jsValue: JsValue) = ProductProtocol.commandsFormat.reads(jsValue)

  def toLocation(id: String)(implicit request: RequestHeader): String = {
    ReverseQueryCtrl.get(id).absoluteURL()
  }

  def toAggregateId(id: String): ProductNumber = ProductNumber.fromString(id)
}