package io.funcqrs.behavior

import io.funcqrs.ClassTagImplicits

import scala.reflect.ClassTag

/** extractor to convert a total function into a partial function internally _*/
abstract class ClassTagExtractor[T: ClassTag] {

  def unapply(obj: T): Option[T] = {
    // need classTag because of erasure as we must be able to find back the original type
    if (obj.getClass == ClassTagImplicits[T].runtimeClass) Some(obj)
    else None
  }
}
