package shop.api

import play.api.mvc.{Controller, Result}

trait RestRecovery {
  this: Controller =>

  def recoverRest: PartialFunction[Throwable, Result] = {
    case e: IllegalArgumentException => BadRequest(e.getMessage)
    case e: NoSuchElementException   => NotFound(e.getMessage)

  }

}
