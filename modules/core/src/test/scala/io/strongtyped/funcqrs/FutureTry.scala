package io.strongtyped.funcqrs

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.Try

trait FutureTry {

  implicit class FutureTryOps[A](fut: Future[A]) {
    def asTry = {
      Try(Await.result(fut, 3 seconds))
    }
  }

}
