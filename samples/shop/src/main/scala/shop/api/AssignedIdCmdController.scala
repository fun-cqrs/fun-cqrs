package shop.api

import io.funcqrs.akka.AggregateServiceWithAssignedId
import play.api.libs.json.{ JsError, JsSuccess }
import play.api.mvc.{ Action, RequestHeader }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait AssignedIdCmdController {
  this: CommandController =>

  def aggregateId(id: String): Aggregate#Id

  def toLocation(id: String)(implicit request: RequestHeader): String

  def service: AggregateServiceWithAssignedId[Aggregate] =
    aggregateService.asInstanceOf[AggregateServiceWithAssignedId[Aggregate]]

  def create(id: String) = Action.async(parse.json) { implicit request =>
    val createCmd = toCommand(request.body)
    createCmd match {
      case JsSuccess(cmd, _) =>
        service
          .newInstance(aggregateId(id), cmd)
          .result()
          .map { result =>
            Created.withHeaders("Location" -> toLocation(id))
          }
      case e: JsError => Future.successful(BadRequest(JsError.toJson(e)))
    }
  }

}
