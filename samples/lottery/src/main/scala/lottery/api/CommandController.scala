package lottery.api

import akka.actor.ActorRef
import akka.pattern._
import akka.util.Timeout
import io.strongtyped.funcqrs.{ Aggregate, DomainCommand }
import play.api.libs.json._
import play.api.mvc.{ Action, Controller }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

trait CommandController extends Controller {

  implicit def timeout: Timeout = Timeout(300 millis)

  type AggregateType <: Aggregate

  def aggregateManager: ActorRef

  def toCommand(jsValue: JsValue): JsResult[DomainCommand]

  def toAggregateId(id: String): AggregateType#Id

  def update(id: String) = Action.async(parse.json) { request =>

    val updateCmd = toCommand(request.body)

    updateCmd match {
      case JsSuccess(cmd, _) =>
        (aggregateManager ? (toAggregateId(id), cmd)).map(_ => Ok("done"))

      case e: JsError => Future.successful(BadRequest(JsError.toJson(e)))
    }

  }

}
