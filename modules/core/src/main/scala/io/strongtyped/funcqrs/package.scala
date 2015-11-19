package io.strongtyped

import scala.concurrent.Future

package object funcqrs {

  type HandleEvent = PartialFunction[DomainEvent, Future[Unit]]

  @deprecated(message = "Use ProtocolLike instead", since = "0.0.4")
  type ProtocolDef = ProtocolLike

}