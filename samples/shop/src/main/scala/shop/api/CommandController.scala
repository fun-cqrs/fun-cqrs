package shop.api

import akka.util.Timeout
import io.funcqrs.AggregateLike
import io.funcqrs.akka.AggregateService
import play.api.libs.json._
import play.api.mvc.{ Action, Controller }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

trait CommandController extends Controller {

  implicit def timeout: Timeout = Timeout(300 millis)

  type Aggregate <: AggregateLike

  val aggregateService: AggregateService[Aggregate]

  def toCommand(jsValue: JsValue): JsResult[aggregateService.Command]

  def toAggregateId(id: String): aggregateService.Id

  def update(id: String) = Action.async(parse.json) { request =>

    val updateCmd = toCommand(request.body)

    updateCmd match {
      case JsSuccess(cmd, _) =>
        aggregateService
          .update(toAggregateId(id))(cmd)
          .result()
          .map(_ => Ok("done"))

      case e: JsError => Future.successful(BadRequest(JsError.toJson(e)))
    }

  }

}
