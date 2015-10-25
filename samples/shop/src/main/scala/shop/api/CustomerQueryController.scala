package shop.api

import play.api.libs.json.Writes
import shop.domain.model.{ CustomerView, CustomerId }
import shop.domain.service.CustomerViewRepo
import com.softwaremill.macwire._

class CustomerQueryController(val viewRepo: CustomerViewRepo @@ CustomerView.type) extends QueryController {

  type ViewRepo = CustomerViewRepo

  def toAggregateId(id: String): viewRepo.Identifier = CustomerId.fromString(id)

  implicit def viewModelWrites: Writes[ViewRepo#Model] = CustomerView.format
}
