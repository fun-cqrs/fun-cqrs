package shop.api

import akka.actor.ActorRef
import com.softwaremill.macwire._
import play.api.libs.json.{JsResult, JsValue}
import shop.domain.model.ProductProtocol.ProductCommand
import shop.domain.model.{Product, ProductNumber, ProductProtocol}

import scala.language.postfixOps

class ProductCmdController(val aggregateManager: ActorRef @@ Product.type)
  extends CommandController with AssignedId {

  type AggregateType = Product
  def aggregateId(id: String): ProductNumber = ProductNumber(id)

  def toCommand(jsValue: JsValue): JsResult[ProductCommand] = ProductProtocol.commandsFormat.reads(jsValue)

  def location(id: String): String = s"/product/$id"

  def toAggregateId(id: String): ProductNumber = ProductNumber.fromString(id)
}