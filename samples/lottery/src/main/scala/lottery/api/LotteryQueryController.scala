package lottery.api

import play.api.libs.json.Writes
import play.api.mvc.Controller
import lottery.domain.model.{ LotteryId, LotteryView }
import lottery.domain.service.LotteryViewRepo
import com.softwaremill.macwire._

class LotteryQueryController(val viewRepo: LotteryViewRepo @@ LotteryView.type) extends QueryController with Controller {

  type ViewRepo = LotteryViewRepo

  implicit def viewModelWrites: Writes[LotteryView] = LotteryView.format

  def toAggregateId(id: String): LotteryId = LotteryId.fromString(id)

}