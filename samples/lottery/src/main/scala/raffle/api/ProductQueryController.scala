package raffle.api

import play.api.libs.json.Writes
import play.api.mvc.Controller
import raffle.domain.model.{RaffleId, RaffleView}
import raffle.domain.service.RaffleViewRepo
import com.softwaremill.macwire._

class RaffleQueryController(val viewRepo: RaffleViewRepo @@ RaffleView.type) extends QueryController with Controller {

  type ViewRepo = RaffleViewRepo

  implicit def viewModelWrites: Writes[RaffleView] = RaffleView.format

  def toAggregateId(id: String): RaffleId = RaffleId.fromString(id)

}