package io.funcqrs

import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }
import scala.language.postfixOps
import scala.util.Try

trait FutureTry extends ScalaFutures {

  implicit class FutureTryOps[A](fut: Future[A]) {
    def asTry = {
      Try(Await.result(fut, 3 seconds))
    }
  }
}
