package io.funcqrs

import java.time.OffsetDateTime
import java.util.UUID

import play.api.libs.json._

import scala.util.Try

trait PlayJsonFormats {

  implicit val offsetDateTimeFormat = new Format[OffsetDateTime] {
    override def reads(json: JsValue) = json match {
      case JsString(s) => JsSuccess(OffsetDateTime.parse(s))
      case _ => JsError("error.expected.jsstring")
    }

    override def writes(o: OffsetDateTime): JsValue = JsString(o.toString)
  }

  private def readUUID(value: String): JsResult[UUID] = {
    Try(UUID.fromString(value))
      .map(JsSuccess(_))
      .getOrElse(JsError("error.expected.uuid"))
  }

  def readJsonId[T](json: JsValue)(toJsResult: (String) => JsResult[T]) = {
    json match {
      case JsString(id) => toJsResult(id)
      case _ => JsError("error.expected.jsstring")
    }
  }

  implicit val commandIdFormat = new Format[CommandId] {
    override def reads(json: JsValue): JsResult[CommandId] =
      readJsonId(json)(id => readUUID(id).map(CommandId))

    override def writes(o: CommandId): JsValue = JsString(o.value.toString)
  }

  implicit val eventIdFormat = new Format[EventId] {
    override def reads(json: JsValue): JsResult[EventId] =
      readJsonId(json)(id => readUUID(id).map(EventId))

    override def writes(o: EventId): JsValue = JsString(o.value.toString)
  }

  implicit val tagFormat = Json.format[Tag]

  /**
   * Json format for case classes that just wrap a String value. The json representation is just the string.
   */
  def simpleStringWrapper[W](creator: (String) => W)(extractor: W => String): Format[W] = {

    new Format[W] {
      override def reads(json: JsValue): JsResult[W] = {
        json.validate[JsString].map {
          jsString => creator(jsString.value)
        }
      }

      override def writes(o: W): JsValue = {
        JsString(extractor(o))
      }
    }
  }
}

object PlayJsonFormats extends PlayJsonFormats