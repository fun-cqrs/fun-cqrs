package io

import scala.concurrent.Future
import scala.reflect.ClassTag

package object funcqrs {

  object ClassTagImplicits {
    def apply[CT: ClassTag]: ClassTag[CT] = implicitly[ClassTag[CT]]
  }

  type HandleEvent = PartialFunction[DomainEvent, Future[Unit]]

  /**
    * Type alias to Any, the only reason for having it is because it reads
    * better when defining methods signatures.
    */
  type AnyEvent = Any
}
