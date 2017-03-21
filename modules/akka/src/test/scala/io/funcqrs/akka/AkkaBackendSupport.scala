package io.funcqrs.akka

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import io.funcqrs.akka.backend.AkkaBackend
import io.funcqrs.backend.Query
import io.funcqrs.behavior.Types
import org.scalatest.{ BeforeAndAfterAll, Suite }

import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }
import scala.reflect.ClassTag

trait AkkaBackendSupport extends Suite with BeforeAndAfterAll {

  protected lazy val actorSys: ActorSystem = ActorSystem("test", ConfigFactory.load("application.conf"))

  lazy val backend = new AkkaBackend {
    override val actorSystem: ActorSystem = actorSys
    def sourceProvider(query: Query): EventsSourceProvider = ???
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    configureBackend(backend)
  }

  def aggregateRef[A](implicit types: Types[A], tag: ClassTag[A]) = backend.aggregateRef[A]

  def configureBackend(backend: AkkaBackend): Unit

  override protected def afterAll(): Unit = {
    Await.ready(backend.actorSystem.terminate(), 10.seconds)
  }

  implicit class FailedFuture[T](fut: Future[T]) {
//    def failed
  }
}
