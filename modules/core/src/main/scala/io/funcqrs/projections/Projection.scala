package io.funcqrs.projections

import io.funcqrs.AnyEvent

import scala.concurrent.Future
import scala.language.higherKinds
import scala.util.control.NonFatal

trait Projection {

  type ReceiveEvent  = PartialFunction[EventEnvelop, Future[Unit]]
  type HandleFailure = PartialFunction[(EventEnvelop, Throwable), Future[Unit]]

  def name: String = this.getClass.getSimpleName

  def receiveEvent: ReceiveEvent

  def onFailure: HandleFailure = PartialFunction.empty

  final def onEvent(envelop: EventEnvelop): Future[Unit] = {
    if (receiveEvent.isDefinedAt(envelop)) {
      import scala.concurrent.ExecutionContext.Implicits.global
      receiveEvent(envelop).recoverWith {
        case NonFatal(exp) if onFailure.isDefinedAt((envelop, exp)) => onFailure(envelop, exp)
      }
    } else {
      Future.successful(())
    }
  }

}

case class EventEnvelop(event: AnyEvent, offset: Long)
