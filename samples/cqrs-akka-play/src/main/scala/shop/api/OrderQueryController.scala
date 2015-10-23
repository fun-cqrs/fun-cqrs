package shop.api

import play.api.libs.json.Writes
import shop.domain.model.{ OrderNumber, OrderView }
import shop.domain.service.OrderViewRepo

class OrderQueryController(val viewRepo: OrderViewRepo) extends QueryController {
  type ViewRepo = OrderViewRepo
  def toAggregateId(id: String): OrderNumber = OrderNumber.fromString(id)
  implicit def viewModelWrites: Writes[OrderView] = OrderView.writerOrderView
}
