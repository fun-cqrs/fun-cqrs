package io.strongtyped

import scala.concurrent.Future

package object funcqrs {

  type HandleEvent = PartialFunction[DomainEvent, Future[Unit]]

}