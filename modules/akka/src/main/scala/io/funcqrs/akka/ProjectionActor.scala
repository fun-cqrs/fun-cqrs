package io.funcqrs.akka

import java.util.concurrent.TimeoutException

import akka.actor._
import akka.pattern._
import akka.stream.{ AbruptTerminationException, ActorMaterializer }
import akka.stream.scaladsl.{ Sink, Source }
import akka.util.Timeout
import io.funcqrs.EventWithCommandId
import io.funcqrs.akka.ProjectionActor.Start
import io.funcqrs.akka.util.ConfigReader.projectionConfig
import io.funcqrs.config.CustomOffsetPersistenceStrategy
import io.funcqrs.projections.{ Projection, PublisherFactory }

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{ Failure, Success }

object ProjectionActor {

  case object Start

  case class Done(evt: Any)

  case class OffsetPersisted(offset: Long)

}

abstract class ProjectionActor[O, E](
    projection: Projection[E],
    publisherFactory: PublisherFactory[O, E]
) extends Actor
    with ActorLogging {

  implicit val timeout = Timeout(5 seconds)
  implicit val mat     = ActorMaterializer()

  import context.dispatcher

  var lastProcessedOffset: Option[O] = None

  private val projectionTimeout =
    projectionConfig(projection.name).getDuration("async-projection-timeout", 5.seconds)

  def saveCurrentOffset(offset: O): Future[Unit]

  def recoveryCompleted(): Unit = {
    log.debug("ProjectionActor: starting projection... {}", projection)
    self ! Start
  }

  private def eventuallySendToParent(event: Any) = {
    event match {
      // send last processed event to parent
      case e: EventWithCommandId =>
        log.debug("Processed {}, sending to parent {}", event, context.parent)
        context.parent ! event
      case _ => // do nothing otherwise
    }
  }

  override def receive: Receive = streaming

  def streaming: Receive = {
    case Start => startStreaming()
  }

  private def withTimeout[A](fut: Future[A], contextLog: String): Future[A] = {
    // defines a timeout in case projection Future never terminates
    val eventualTimeout =
      after(duration = projectionTimeout, using = context.system.scheduler) {
        Future.failed(new TimeoutException(s"Timed out projection ${projection.name}. $contextLog"))
      }
    // one or another, first wins
    Future firstCompletedOf Seq(fut, eventualTimeout)
  }

  def startStreaming() = {

    Source
      .fromPublisher(publisherFactory.from(lastProcessedOffset))
      .mapAsync(1) { // process the event
        case (offset, payload) =>
          withTimeout(
            projection.onEvent(payload),
            s"Processing offset $offset - event: $payload"
          ).map(_ => (offset, payload))
      }
      .mapAsync(1) { // save the offset
        case (offset, payload) =>
          log.debug("Processed {}, sending to parent {}", payload, context.parent)
          context.parent ! payload // send last processed event to parent
          withTimeout(
            saveCurrentOffset(offset),
            s"Saving offset $offset - event: $payload"
          )
      }
      .runWith(Sink.ignore)
      .onComplete {

        case Success(_) =>
          context.stop(self)

        case Failure(_: AbruptTerminationException) =>
          log.warning("ActorSystem shutdown. Stopping projection: {}", projection.name)

        case Failure(e) => // receive an error from the stream
          log.error(e, "Error while processing stream for projection [{}]", projection.name)
          context.stop(self)
      }
  }

}

/**
  * A ProjectionActor that never saves the offset
  * causing the event stream to be read from start on each app restart
  */
class ProjectionActorWithoutOffsetPersistence[O, E](
    projection: Projection[E],
    publisherFactory: PublisherFactory[O, E]
) extends ProjectionActor(projection, publisherFactory)
    with OffsetNotPersisted[O, E]

object ProjectionActorWithoutOffsetPersistence {

  def props[O, E](
      projection: Projection[E],
      publisherFactory: PublisherFactory[O, E]
  ): Props = {

    Props(new ProjectionActorWithoutOffsetPersistence(projection, publisherFactory))
  }

}

/** A ProjectionActor that saves the offset using a [[CustomOffsetPersistenceStrategy]] */
class ProjectionActorWithCustomOffsetPersistence[O, E](
    projection: Projection[E],
    publisherFactory: PublisherFactory[O, E],
    customOffsetPersistence: CustomOffsetPersistenceStrategy[O]
) extends ProjectionActor(projection, publisherFactory)
    with PersistedOffsetCustom[O, E] {

  def saveCurrentOffset(offset: O): Future[Unit] = {
    customOffsetPersistence.saveCurrentOffset(offset)
  }

  /** Returns the current offset as persisted in DB */
  def readOffset: Future[Option[O]] = customOffsetPersistence.readOffset
}

object ProjectionActorWithCustomOffsetPersistence {

  def props[O, E](
      projection: Projection[E],
      publisherFactory: PublisherFactory[O, E],
      customOffsetPersistence: CustomOffsetPersistenceStrategy[O]
  ): Props = {

    Props(new ProjectionActorWithCustomOffsetPersistence(projection, publisherFactory, customOffsetPersistence))
  }

}
