package lottery.app

import io.strongtyped.funcqrs.CommandException
import play.api.http.{ Status, HttpErrorHandler }
import play.api.libs.json.Json
import play.api.mvc.{ Results, Result, RequestHeader }

import scala.concurrent.Future

class RestHttpErrorHandler extends HttpErrorHandler {

  def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] = {
    Future.successful(jsonMessage(message, statusCode))
  }

  def onServerError(request: RequestHeader, exception: Throwable): Future[Result] = {

    def mapTo(code: Int): Result =
      jsonMessage(exception.getMessage, code)

    val result =
      exception match {
        case e: IllegalArgumentException => mapTo(Status.BAD_REQUEST)
        case e: NoSuchElementException   => mapTo(Status.NOT_FOUND)
        case e: CommandException         => mapTo(Status.PRECONDITION_FAILED)
        case e                           => mapTo(Status.INTERNAL_SERVER_ERROR)
      }

    Future.successful(result)
  }

  private def jsonMessage(msg: String, code: Int): Result = {
    val json = Json.obj(
      "code" -> code,
      "errorMessage" -> msg
    )
    Results.Status(code)(json)
  }
}
