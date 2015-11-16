package lottery.domain.model

import java.time.OffsetDateTime

import play.api.libs.json._

object OffsetDateTimeFormat {
  implicit val offsetDateTimeFormat = new Format[OffsetDateTime] {
    override def reads(json: JsValue) = json match {
      case JsString(s) => JsSuccess(OffsetDateTime.parse(s))
      case _           => JsError("error.expected.jsstring")
    }

    override def writes(o: OffsetDateTime): JsValue = JsString(o.toString)
  }
}
