package shop.api

import akka.actor.ActorRef
import com.softwaremill.macwire._
import play.api.libs.json.{JsResult, JsValue}
import play.api.mvc.RequestHeader
import shop.domain.model.ProductProtocol.ProductCommand
import shop.domain.model.{Product, ProductNumber, ProductProtocol}
import shop.api.routes.{ProductQueryController => ReverseQueryCtrl}
import scala.language.postfixOps

class ProductCmdController(val aggregateManager: ActorRef @@ Product.type)
  extends CommandController with AssignedId {

  type AggregateType = Product
  def aggregateId(id: String): ProductNumber = ProductNumber(id)

  def toCommand(jsValue: JsValue): JsResult[ProductCommand] = ProductProtocol.commandsFormat.reads(jsValue)

  def toLocation(id: String)(implicit request: RequestHeader): String = {
    ReverseQueryCtrl.get(id).absoluteURL()
  }

  def toAggregateId(id: String): ProductNumber = ProductNumber.fromString(id)
}