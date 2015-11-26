package io.funcqrs

//import org.scalatest.concurrent.PatienceConfiguration.{Interval, Timeout}
//import org.scalatest.concurrent.{ScalaFutures, Futures}
//
//import scala.util.{Failure, Success, Try}
//
//trait FailedFutures extends Futures with ScalaFutures{
//
//  final def whenFailed[T](future: FutureConcept[T])(fun: Throwable => Unit): Unit = {
//
//    val config = implicitly[PatienceConfig]
//
//    Try(future.isReadyWithin(config.timeout)) match {
//      case Success(_) => new RuntimeException("expecting a failure")
//      case Failure(e) => fun(e.getCause)
//    }
//  }
//
//}