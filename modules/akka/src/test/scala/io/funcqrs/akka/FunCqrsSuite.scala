package io.funcqrs.akka

import akka.actor.{ ActorRef, ActorSystem }
import io.funcqrs.AggregateLike
import io.funcqrs.akka.dsl.FunCqrsDsl

trait FunCqrsSuite {

  def actorSystem: ActorSystem
  implicit lazy val funCQRS = new FunCQRS(actorSystem)

}
