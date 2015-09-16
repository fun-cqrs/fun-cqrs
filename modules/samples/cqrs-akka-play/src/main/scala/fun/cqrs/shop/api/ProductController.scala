package fun.cqrs.shop.api

import akka.actor.ActorRef
import akka.util.Timeout
import com.softwaremill.macwire._
import fun.cqrs.shop.domain.model.{Product, ProductId, ProductProtocol}
import play.api.libs.json.JsValue

import scala.concurrent.duration._
import scala.language.postfixOps


class ProductController(val aggregateManager: ActorRef @@ Product.type) extends AggregateController[Product] {

  implicit def timeout: Timeout = Timeout(300 millis)

  def toCommand(jsValue: JsValue) = {
    ProductProtocol.commandsFormat.reads(jsValue)
  }

  def location(id: String): String = s"/product/$id"

  def toAggregateId(id: String): ProductId = ProductId.fromString(id)
}