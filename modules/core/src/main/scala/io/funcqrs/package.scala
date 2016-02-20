package io

import scala.concurrent.Future
import scala.reflect.ClassTag

package object funcqrs {

  object ClassTagImplicits {
    def apply[CT: ClassTag]: ClassTag[CT] = implicitly[ClassTag[CT]]
  }

  type HandleEvent = PartialFunction[DomainEvent, Future[Unit]]

  @deprecated(message = "Use AggregateId instead", since = "0.3.0")
  type AggregateID = AggregateId
}