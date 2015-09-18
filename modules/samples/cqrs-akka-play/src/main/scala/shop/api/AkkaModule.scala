package shop.api

import akka.actor.ActorSystem

trait AkkaModule {

  def actorSystem: ActorSystem

}
