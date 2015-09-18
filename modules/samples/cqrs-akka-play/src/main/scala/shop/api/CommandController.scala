package shop.api

import akka.actor.ActorRef
import akka.util.Timeout
import fun.cqrs.{DomainCommand, Aggregate}
import fun.cqrs.akka.AggregateActor.SuccessfulCommand
import play.api.libs.json._
import play.api.mvc.{Controller, Action}
import akka.pattern._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.postfixOps

import scala.concurrent.duration._

trait CommandController extends Controller {

  implicit def timeout: Timeout = Timeout(300 millis)

  type AggregateType <: Aggregate

  def aggregateManager: ActorRef

  def toCommand(jsValue: JsValue): JsResult[DomainCommand]

  def toAggregateId(id: String): AggregateType#Identifier


  def update(id: String) = Action.async(parse.json) { request =>

    val updateCmd = toCommand(request.body)

    updateCmd match {
      case JsSuccess(cmd, _) =>
        (aggregateManager ?(toAggregateId(id), cmd))
          .mapTo[SuccessfulCommand]
          .map { _ =>
          Ok("done")
        }

      case e: JsError => Future.successful(BadRequest(JsError.toJson(e)))
    }

  }

}
