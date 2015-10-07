package shop.api

import akka.actor.ActorRef
import com.softwaremill.macwire._
import play.api.libs.json.{JsResult, JsValue}
import shop.domain.model.OrderProtocol.OrderCommand
import shop.domain.model.{Order, OrderNumber, OrderProtocol}


class OrderCmdController(val aggregateManager: ActorRef @@ Order.type)
  extends CommandController with AssignedId {

  type AggregateType = Order

  def location(id: String): String = s"/order/$id"
  def aggregateId(id: String): OrderNumber = OrderNumber(id)

  def toCommand(jsValue: JsValue): JsResult[OrderCommand] =
    OrderProtocol.commandFormats.reads(jsValue)

  def toAggregateId(id: String): OrderNumber = OrderNumber.fromString(id)
}
