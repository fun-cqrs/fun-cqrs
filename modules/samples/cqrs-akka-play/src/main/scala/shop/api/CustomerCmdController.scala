package shop.api

import akka.actor.ActorRef
import com.softwaremill.macwire._
import fun.cqrs.DomainCommand
import play.api.libs.json.{JsResult, JsValue}
import shop.domain.model.{Customer, CustomerId, CustomerProtocol}


class CustomerCmdController(val aggregateManager: ActorRef @@ Customer.type)
  extends CommandController with AssignedId {

  type AggregateType = Customer

  def aggregateId(id: String): CustomerId = CustomerId(id)

  def toCommand(jsValue: JsValue): JsResult[DomainCommand] =
    CustomerProtocol.commandsFormat.reads(jsValue)

  def toAggregateId(id: String): CustomerId = CustomerId.fromString(id)

  def location(id: String): String = s"/customer/$id"
}
