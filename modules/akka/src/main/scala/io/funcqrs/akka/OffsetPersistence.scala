package io.funcqrs.akka

import akka.actor.Stash
import akka.persistence._
import io.funcqrs.akka.PersistedOffsetAkka.LastProcessedEventOffset

import scala.concurrent.{ Promise, Future }
import scala.util.control.NonFatal

/** Defines how the projection offset should be persisted */
trait OffsetPersistence {
  this: ProjectionActor =>

  def saveCurrentOffset(offset: Long): Future[Unit]
}

/** Does NOT persist the offset forcing a full stream read each time */
trait OffsetNotPersisted extends OffsetPersistence {
  this: ProjectionActor =>

  def saveCurrentOffset(offset: Long): Future[Unit] = Future.successful(())

  // nothing to recover, thus recoveryCompleted on preStart
  override def preStart(): Unit = recoveryCompleted()
}

/** Read and save from a database. */
trait PersistedOffsetCustom extends OffsetPersistence {
  this: ProjectionActor =>

  def saveCurrentOffset(offset: Long): Future[Unit]

  /** Returns the current offset as persisted in DB */
  def readOffset: Future[Option[Long]]

  /** On preStart we read the offset from db and start the events streaming */
  override def preStart(): Unit = {
    import scala.concurrent.ExecutionContext.Implicits.global
    readOffset.map { offset =>
      lastProcessedOffset = offset
      recoveryCompleted()
    }.recover {
      case NonFatal(e) =>
        log.error(e, "Couldn't read offset")
        // can't read offset?
        // stop the actor - BackoffSupervisor must take care of this
        context.stop(self)
    }
  }
}

/**
 * Persist Last Processed Event Offset as Projection Event in akka-persistence
 *
 * This implementation is a quick win for those that simply want to persist the offset without caring about
 * the persistence layer.
 *
 * However, the drawback is that most (if not all) akka-persistence plugins will
 * save it as binary data which make it difficult to inspect the DB to get to know the last processed event.
 */
trait PersistedOffsetAkka extends OffsetPersistence with PersistentActor with Stash {
  self: ProjectionActor =>

  def persistenceId: String

  override def receive = receiveCommand

  override def receiveCommand: Receive = acceptingEvents

  override val receiveRecover: Receive = {

    case SnapshotOffer(metadata, offset: Long) =>
      log.debug(s"[$persistenceId] snapshot offer - last processed event offset $offset")
      lastProcessedOffset = Some(offset)

    case LastProcessedEventOffset(offset) =>
      log.debug(s"[$persistenceId] - last processed event offset $offset")
      lastProcessedOffset = Option(offset)

    case _: RecoveryCompleted =>
      log.debug(s"[$persistenceId] recovery completed - last processed event offset $lastProcessedOffset")
      recoveryCompleted()

    case unknown => log.debug(s"Unknown message on recovery: $unknown")

  }

  def saveCurrentOffset(offset: Long): Future[Unit] = {

    // we need to conform with OffsetPersistence API and return a Future[Unit]
    // Seems odd, but thet ProjectionActor that may get this trait mixed in
    // is not aware (and should not be aware) that this trait makes him a PersistentActor
    val saveOffsetPromise = Promise[Unit]()

    persist(LastProcessedEventOffset(offset)) { evt =>

      log.debug(s"Projection: $persistenceId - saving domain event offset $offset")
      val seqNrToDelete = lastSequenceNr - 1

      // delete old message if any, no need to wait
      if (seqNrToDelete > 0) {
        log.debug(s"Projection: $persistenceId - deleting previous projection event: $seqNrToDelete")
        deleteMessages(seqNrToDelete)
      }

      lastProcessedOffset = Option(offset)
      saveOffsetPromise.success(())
    }

    saveOffsetPromise.future
  }

}

object PersistedOffsetAkka {
  case class LastProcessedEventOffset(offset: Long)
}