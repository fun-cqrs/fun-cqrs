package io.funcqrs.dsl

import scala.language.{ higherKinds, implicitConversions }

object BehaviorDsl {

  val api = Api
  object Api extends SpecSupport with BindingSupport

}

