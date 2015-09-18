package fun.cqrs.shop.api

import akka.actor.ActorRef
import akka.util.Timeout
import com.softwaremill.macwire._
import fun.cqrs.shop.domain.model.{ProductNumber, Product, ProductProtocol}
import fun.cqrs.shop.domain.service.ProductViewRepo
import play.api.libs.json.{Json, JsValue}
import play.api.mvc.Action

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.concurrent.ExecutionContext.Implicits.global

class ProductController(val aggregateManager: ActorRef @@ Product.type, productViewRepo: ProductViewRepo)
  extends AggregateController with AssignedId {

  type AggregateType = Product


  implicit def timeout: Timeout = Timeout(300 millis)

  def toCommand(jsValue: JsValue) = {
    ProductProtocol.commandsFormat.reads(jsValue)
  }

  def location(id: String): String = s"/product/$id"

  def toAggregateId(id: String): ProductNumber = ProductNumber.fromString(id)

  def get(id: String) = Action.async {
    val productViewRes = productViewRepo.find(ProductNumber.fromString(id))
    productViewRes.map { productView =>
      Ok(Json.toJson(productView))
    }
  }

  def list = Action.async {
    productViewRepo.fetchAll.map { products =>
      val json = Json.toJson(products)
      Ok(json)
    }
  }
}