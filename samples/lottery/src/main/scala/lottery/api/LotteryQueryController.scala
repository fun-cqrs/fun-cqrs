package lottery.api

import lottery.domain.model.{ LotteryId, LotteryView }
import lottery.domain.service.LotteryViewRepo
import play.api.libs.json.Writes
import play.api.mvc.Controller

class LotteryQueryController(val viewRepo: LotteryViewRepo) extends QueryController with Controller {

  type ViewRepo = LotteryViewRepo

  implicit def viewModelWrites: Writes[LotteryView] = LotteryView.format

  def toAggregateId(id: String): LotteryId = LotteryId.fromString(id)

}