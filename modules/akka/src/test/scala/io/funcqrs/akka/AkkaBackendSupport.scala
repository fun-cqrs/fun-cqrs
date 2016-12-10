package io.funcqrs.akka

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import io.funcqrs.akka.backend.AkkaBackend
import io.funcqrs.backend.Query
import org.scalatest.{ BeforeAndAfter, BeforeAndAfterAll, Suite }

import scala.concurrent.duration._
import scala.concurrent.Await

trait AkkaBackendSupport extends Suite with BeforeAndAfterAll {

  private val actorSys: ActorSystem = ActorSystem("test", ConfigFactory.load("application.conf"))

  lazy val backend = new AkkaBackend {
    override val actorSystem: ActorSystem = actorSys
    def sourceProvider(query: Query): EventsSourceProvider = ???
  }

  override protected def afterAll(): Unit = {
    Await.ready(backend.actorSystem.terminate(), 10.seconds)
  }
}
