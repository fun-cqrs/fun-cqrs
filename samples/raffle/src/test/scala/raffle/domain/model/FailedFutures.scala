package raffle.domain.model

import org.scalatest.concurrent.{ Futures, ScalaFutures }

import scala.util.{ Failure, Success, Try }

trait FailedFutures extends Futures with ScalaFutures {

  final def whenFailed[T](future: FutureConcept[T])(fun: Throwable => Unit): Unit = {

    val config = implicitly[PatienceConfig]

    Try(future.isReadyWithin(config.timeout)) match {
      case Success(_) => throw new RuntimeException("expecting a failure")
      case Failure(e) => fun(e.getCause)
    }
  }

}
