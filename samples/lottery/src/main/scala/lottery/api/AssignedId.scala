package lottery.api

import akka.pattern._
import play.api.libs.json.{ JsError, JsSuccess }
import play.api.mvc.{ Action, RequestHeader }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait AssignedId {
  this: CommandController =>

  def aggregateId(id: String): AggregateType#Id

  def toLocation(id: String)(implicit request: RequestHeader): String

  def create(id: String) = Action.async(parse.json) { implicit request =>
    val createCmd = toCommand(request.body)
    createCmd match {
      case JsSuccess(cmd, _) =>
        (aggregateManager ? (aggregateId(id), cmd)).map { result =>
          Created.withHeaders("Location" -> toLocation(id))
        }
      case e: JsError => Future.successful(BadRequest(JsError.toJson(e)))
    }
  }

}
