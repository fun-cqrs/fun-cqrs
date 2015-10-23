package lottery.api

import akka.actor.ActorSystem
import io.strongtyped.funcqrs.akka.FunCQRS

trait AkkaModule {

  def actorSystem: ActorSystem

  val funCQRS = new FunCQRS(actorSystem)
}
