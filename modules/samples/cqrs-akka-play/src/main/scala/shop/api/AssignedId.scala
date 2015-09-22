package shop.api

import fun.cqrs.AggregateIdentifier
import shop.domain.model.ProductNumber

import scala.concurrent.ExecutionContext.Implicits.global
import fun.cqrs.akka.AggregateActor.SuccessfulCommand
import play.api.libs.json.{JsError, JsSuccess}
import play.api.mvc.Action

import scala.concurrent.Future
import akka.pattern._

trait AssignedId {
  this: CommandController =>


  def aggregateId(id: String): AggregateType#Identifier

  def location(id: String): String

  def create(id: String) = Action.async(parse.json) { request =>
    val createCmd = toCommand(request.body)
    createCmd match {
      case JsSuccess(cmd, _) =>
        (aggregateManager ?(aggregateId(id), cmd))
          .mapTo[SuccessfulCommand]
          .map { result =>
          Created.withHeaders("Location" -> location(result.events.head.metadata.aggregateId.value))
        }
      case e: JsError        => Future.successful(BadRequest(JsError.toJson(e)))
    }
  }

}
