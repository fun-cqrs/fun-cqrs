package lottery.api

import akka.actor.ActorSystem
import io.strongtyped.funcqrs.akka.CQRSSystem
import com.softwaremill.macwire._

trait AkkaModule {

  def actorSystem: ActorSystem

  val funCQRS = new CQRSSystem(actorSystem)
}
