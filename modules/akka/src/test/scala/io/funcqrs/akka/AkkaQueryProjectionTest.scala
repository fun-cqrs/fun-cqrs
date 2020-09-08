package io.funcqrs.akka

import akka.persistence.inmemory.query.scaladsl.InMemoryReadJournal
import akka.persistence.query.{PersistenceQuery, Sequence}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.util.Timeout
import io.funcqrs.akka.TestModel.{User, UserId}
import io.funcqrs.akka.backend.AkkaBackend
import io.funcqrs.config.AkkaOffsetPersistenceStrategy
import io.funcqrs.config.api._
import io.funcqrs.projections.{Projection, PublisherFactory}
import org.reactivestreams.Publisher
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Seconds, Span}

import scala.concurrent.Future
import scala.concurrent.duration._

class AkkaQueryProjectionTest extends AnyFlatSpecLike with Matchers with ScalaFutures with AkkaBackendSupport with Eventually {

  implicit val timeout = Timeout(500.millis)

  behavior of "A UserProjection"

  it should "receive events and 'persist' it in in-memory repo" in {
    val userRef = backend.aggregateRef[User].forId(UserId.generate())
    userRef ! CreateUser("John", 30)

    eventually {
      repo.find("John").futureValue shouldBe "John"
    }
  }



  // very patient
  override implicit def patienceConfig: PatienceConfig =
    PatienceConfig(timeout = scaled(Span(5, Seconds)))

  lazy val readJournal =
    PersistenceQuery(actorSys)
      .readJournalFor[InMemoryReadJournal](InMemoryReadJournal.Identifier)


  val repo = new UsernameRepo

  override def configureBackend(backend: AkkaBackend): Unit = {

    implicit val system = actorSys

    backend
      .configure {
        aggregate(User.behavior)
      }
      .configure {
        projection(
          projection = new UserProjection(repo),
          publisherFactory = new PublisherFactory[Long, UserEvt] {
            override def from(offset: Option[Long]): Publisher[(Long, UserEvt)] = {

              val akkaOffset = offset.map(Sequence).getOrElse(Sequence(0))

              readJournal
                .eventsByTag(User.tag, akkaOffset)
                .map { akkaEnvelope =>
                  val Sequence(value) = akkaEnvelope.offset
                  (value, akkaEnvelope.event.asInstanceOf[UserEvt])
                }
                .runWith(Sink.asPublisher(false))

            }
          },
          name = "test-user-projection"
        ).withCustomOffsetPersistence(
          AkkaOffsetPersistenceStrategy.offsetAsLong(actorSys, "test-user-projection")
        )
      }
  }

  class UsernameRepo {
    var users = List.empty[String]

    def save(name: String): Unit =
      users = name :: users

    def find(name: String): Future[String] = {
      users
        .find(_ == name)
        .map(Future.successful)
        .getOrElse(Future.failed(new Exception(s"User $name not here yet")))
    }
  }

  class UserProjection(repo: UsernameRepo) extends Projection[UserEvt] {

    override def handleEvent: HandleEvent = sync.HandleEvent {
      case UserCreated(name, _) => repo.save(name)
    }
  }
}
