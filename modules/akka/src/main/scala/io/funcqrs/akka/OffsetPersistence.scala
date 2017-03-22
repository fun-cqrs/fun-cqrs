package io.funcqrs.akka

import scala.concurrent.Future
import scala.util.control.NonFatal

/** Defines how the projection offset should be persisted */
trait OffsetPersistence[O, E] { this: ProjectionActor[O, E] =>

  def saveCurrentOffset(offset: O): Future[Unit]
}

/** Does NOT persist the offset forcing a full stream read each time */
trait OffsetNotPersisted[O, E] extends OffsetPersistence[O, E] { this: ProjectionActor[O, E] =>

  def saveCurrentOffset(offset: O): Future[Unit] = {
    Future.successful(())
  }

  // nothing to recover, thus recoveryCompleted on preStart
  override def preStart(): Unit = recoveryCompleted()
}

/** Read and save from a database. */
trait PersistedOffsetCustom[O, E] extends OffsetPersistence[O, E] { this: ProjectionActor[O, E] =>

  def saveCurrentOffset(offset: O): Future[Unit]

  /** Returns the current offset as persisted in DB */
  def readOffset: Future[Option[O]]

  /** On preStart we read the offset from db and start the events streaming */
  override def preStart(): Unit = {
    import scala.concurrent.ExecutionContext.Implicits.global
    readOffset
      .map { offset =>
        lastProcessedOffset = offset
        recoveryCompleted()
      }
      .recover {
        case NonFatal(e) =>
          log.error(e, "Couldn't read offset")
          // can't read offset?
          // stop the actor - BackoffSupervisor must take care of this
          context.stop(self)
      }
  }
}

object PersistedOffsetAkka {
  case class LastProcessedEventOffset(offset: Long)
}
