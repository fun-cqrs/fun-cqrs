package shop.api

import play.api.libs.json.Writes
import play.api.mvc.Controller
import shop.domain.model.{ProductNumber, ProductView}
import shop.domain.service.ProductViewRepo

class ProductQueryController(val viewRepo: ProductViewRepo) extends QueryController with Controller {

  type ViewRepo = ProductViewRepo

  implicit def viewModelWrites: Writes[ProductView] = ProductView.format

  def toAggregateId(id: String): ProductNumber = ProductNumber.fromString(id)

}