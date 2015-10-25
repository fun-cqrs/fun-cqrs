package lottery.domain.model

import java.time.OffsetDateTime

import play.api.libs.json._

import OffesetDateTimeFormat._

case class LotteryView(name: String,
                       participants: List[LotteryView.Participant] = List(),
                       winner: Option[String] = None,
                       runDate: Option[OffsetDateTime] = None,
                       id: LotteryId)

object OffesetDateTimeFormat {
  implicit val offsetDateTimeFormat = new Format[OffsetDateTime] {
    override def reads(json: JsValue) = json match {
      case JsString(s) => JsSuccess(OffsetDateTime.parse(s))
      case _           => JsError("error.expected.jsstring")
    }

    override def writes(o: OffsetDateTime): JsValue = JsString(o.toString)
  }
}

object LotteryView {

  case class Participant(name: String, date: OffsetDateTime)

  object Participant {
    implicit val format = Json.writes[Participant]
  }

  implicit val format = Json.writes[LotteryView]
}

