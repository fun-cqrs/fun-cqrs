package io

import scala.concurrent.Future
import scala.language.implicitConversions

package object funcqrs {

  sealed trait FutureMagnet {
    def apply(): Future[Unit]
  }

  object FutureMagnet {
    implicit def fromFutureX[X](x: Future[X]): FutureMagnet =
      new FutureMagnet {
        def apply() = {
          import scala.concurrent.ExecutionContext.Implicits.global
          x.map(_ => ())
        }
      }
  }

  type HandleEvent = PartialFunction[DomainEvent, FutureMagnet]

  @deprecated(message = "Use ProtocolLike instead", since = "0.0.4")
  type ProtocolDef = ProtocolLike

}