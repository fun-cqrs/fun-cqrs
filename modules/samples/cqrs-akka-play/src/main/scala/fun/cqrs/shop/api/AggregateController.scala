package fun.cqrs.shop.api

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


trait AggregateController[A <: Aggregate] extends Controller {

  implicit def timeout: Timeout

  def aggregateManager: ActorRef

  def toCommand(jsValue: JsValue): JsResult[DomainCommand]

  def location(id: String): String

  def toAggregateId(id: String): A#Identifier

  def create = Action.async(parse.json) { request =>

    val createCmd = toCommand(request.body)

    createCmd match {
      case JsSuccess(cmd, _) =>
        (aggregateManager ? cmd)
          .mapTo[SuccessfulCommand]
          .map { result =>
          Created.withHeaders("Location" -> location(result.events.head.metadata.aggregateId.value))
        }
      case e: JsError        => Future.successful(BadRequest(JsError.toJson(e)))
    }

  }

  def update(id: String) = Action.async(parse.json) { request =>

    val updateCmd = toCommand(request.body)

    updateCmd match {
      case JsSuccess(cmd, _) =>
        (aggregateManager ?(toAggregateId(id), cmd))
          .mapTo[SuccessfulCommand]
          .map { _ =>
          Ok("done")
        }
      case e: JsError        => Future.successful(BadRequest(JsError.toJson(e)))
    }

  }

}
