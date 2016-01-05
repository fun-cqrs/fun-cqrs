package io

import scala.concurrent.Future

package object funcqrs {

  type HandleEvent = PartialFunction[DomainEvent, Future[Unit]]

  @deprecated(message = "Use AggregateId instead", since = "0.3.0")
  type AggregateID = AggregateId
}