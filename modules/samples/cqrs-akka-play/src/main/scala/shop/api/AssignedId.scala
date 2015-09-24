package shop.api

import akka.pattern._
import fun.cqrs.akka.AggregateActor.SuccessfulCommand
import play.api.libs.json.{JsError, JsSuccess}
import play.api.mvc.Action

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait AssignedId {
  this: CommandController =>


  def aggregateId(id: String): AggregateType#Identifier


  def create(id: String) = Action.async(parse.json) { request =>
    val createCmd = toCommand(request.body)
    createCmd match {
      case JsSuccess(cmd, _) =>
        (aggregateManager ?(aggregateId(id), cmd))
          .mapTo[SuccessfulCommand]
          .map { result =>
          Created.withHeaders("Location" -> id)
        }
      case e: JsError        => Future.successful(BadRequest(JsError.toJson(e)))
    }
  }

}
