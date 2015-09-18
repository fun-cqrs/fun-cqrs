package shop.api

import play.api.libs.json.Writes
import play.api.mvc.Controller
import shop.domain.model.{ProductNumber, ProductView}
import shop.domain.service.ProductViewRepo

class ProductViewController(val viewRepo: ProductViewRepo) extends ViewController with Controller {

  type ViewRepo = ProductViewRepo

  implicit def viewModelWrites: Writes[ProductView] = ProductView.format

  def toAggregateId(id: String): ProductNumber = ProductNumber.fromString(id)

}