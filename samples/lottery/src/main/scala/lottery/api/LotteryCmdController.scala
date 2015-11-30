package lottery.api

import akka.util.Timeout
import io.funcqrs.AggregateAliases
import io.funcqrs.akka.AggregateServiceWithAssignedId
import lottery.api.routes.{ LotteryQueryController => ReverseQueryCtrl }
import lottery.domain.model.{ Lottery, LotteryId, LotteryProtocol }
import play.api.libs.json.{ JsError, JsResult, JsSuccess, JsValue }
import play.api.mvc.{ Action, Controller, RequestHeader }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class LotteryCmdController(val aggregateService: AggregateServiceWithAssignedId[Lottery]) extends Controller with AggregateAliases {

  type Aggregate = aggregateService.Aggregate

  implicit def timeout: Timeout = Timeout(300.millis)

  private def toAggregateId(id: String): LotteryId = LotteryId.fromString(id)
  private def toCommand(jsValue: JsValue): JsResult[aggregateService.Command] = LotteryProtocol.commandsFormat.reads(jsValue)

  def create(id: String) = Action.async(parse.json) { implicit request =>

    val createCmd = toCommand(request.body)
    val aggregateId = LotteryId(id)

    createCmd match {
      case JsSuccess(cmd, _) =>
        val res =
          aggregateService
            .newInstance(aggregateId, cmd)
            .watch("LotteryViewProjectionActor")
            .result()

        res.map { result =>
          Created.withHeaders("Location" -> toLocation(id))
        }
      case e: JsError => Future.successful(BadRequest(JsError.toJson(e)))
    }
  }

  def update(id: String) = Action.async(parse.json) { request =>

    val updateCmd = toCommand(request.body)
    val aggregateId = LotteryId(id)

    updateCmd match {
      case JsSuccess(cmd, _) =>
        val res =
          aggregateService
            .update(aggregateId)(cmd)
            .watch("LotteryViewProjectionActor")
            .result()

        res.map(_ => Ok("done"))

      case e: JsError => Future.successful(BadRequest(JsError.toJson(e)))
    }

  }

  private def toLocation(id: String)(implicit request: RequestHeader): String = {
    ReverseQueryCtrl.get(id).absoluteURL()
  }
}