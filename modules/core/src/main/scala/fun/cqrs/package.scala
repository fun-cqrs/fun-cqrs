package fun

import scala.concurrent.Future

package object cqrs {

  type HandleEvent = PartialFunction[DomainEvent, Future[Unit]]
}
