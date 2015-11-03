package lottery.domain.model

import java.time.OffsetDateTime

import play.api.libs.json._


case class LotteryView(name: String,
                       participants: List[LotteryView.Participant] = List(),
                       winner: Option[String] = None,
                       runDate: Option[OffsetDateTime] = None,
                       id: LotteryId)

object LotteryView {

  import OffsetDateTimeFormat._

  case class Participant(name: String, date: OffsetDateTime)

  object Participant {
    implicit val format = Json.writes[Participant]
  }

  implicit val format = Json.writes[LotteryView]
}

