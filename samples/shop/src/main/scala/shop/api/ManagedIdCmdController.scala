package shop.api

import io.funcqrs.DomainEvent
import io.funcqrs.akka.AggregateServiceWithManagedId
import play.api.libs.json.{ JsError, JsSuccess }
import play.api.mvc.Action
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait ManagedIdCmdController {
  this: CommandController =>

  def toLocation(event: DomainEvent): String

  def service: AggregateServiceWithManagedId[Aggregate] =
    aggregateService.asInstanceOf[AggregateServiceWithManagedId[Aggregate]]

  def create = Action.async(parse.json) { request =>

    val createCmd = toCommand(request.body)

    createCmd match {
      case JsSuccess(cmd, _) =>
        service.newInstance(cmd).result()
          .map { event =>
            Created.withHeaders("Location" -> toLocation(event))
          }
      case e: JsError => Future.successful(BadRequest(JsError.toJson(e)))
    }

  }

}
