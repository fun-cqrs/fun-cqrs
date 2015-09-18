package shop.api

import play.api.libs.json.Writes
import shop.domain.model.{CustomerView, CustomerId}
import shop.domain.service.CustomerViewRepo

class CustomerQueryController(val viewRepo: CustomerViewRepo) extends QueryController {

  type ViewRepo = CustomerViewRepo

  def toAggregateId(id: String): viewRepo.Identifier = CustomerId.fromString(id)

  implicit def viewModelWrites: Writes[ViewRepo#Model] = CustomerView.format
}
