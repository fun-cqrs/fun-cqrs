package shop.api

import akka.actor.ActorSystem
import io.funcqrs.akka.FunCQRS

trait AkkaModule {

  def actorSystem: ActorSystem

  implicit val funCQRS = new FunCQRS(actorSystem)
}
