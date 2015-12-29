package io.funcqrs.akka

import akka.actor.Stash
import akka.persistence._

import scala.concurrent.Future

/** Defines how the projection offset should be persisted */
trait OffsetPersistence {
  this: ProjectionActor =>

  def saveCurrentOffset(offset: Long): Unit
}

/** Does NOT persist the offset forcing a full stream read each time */
trait OffsetNotPersisted extends OffsetPersistence {
  this: ProjectionActor =>

  def saveCurrentOffset(offset: Long): Unit = ()

  // nothing to recover, thus recoveryCompleted on preStart
  override def preStart(): Unit = recoveryCompleted()
}

/** Read and save from a database. */
trait PersistedOffsetCustom extends OffsetPersistence {
  this: ProjectionActor =>

  def saveCurrentOffset(offset: Long): Unit

  /** Returns the current offset as persisted in DB */
  def readOffset: Future[Long]

  /** On preStart we read the offset from db and start the events streaming */
  override def preStart(): Unit = {
    import scala.concurrent.ExecutionContext.Implicits.global
    readOffset.map { offset =>
      lastProcessedOffset = offset
      recoveryCompleted()
    }
  }
}

/**
 * Persist Offset as snapshot in akka-persistence
 *
 * This implementation is a quick win for those that simply want to persist the offset without caring about
 * the persistence layer.
 *
 * However, the drawback is that most (if not all) akka-persistence snapshot plugins will
 * save it as binary data which make it difficult to inspect the DB to get to know the last processed event.
 */
trait PersistedOffsetAkka extends OffsetPersistence with PersistentActor with Stash {
  self: ProjectionActor =>

  def persistenceId: String

  override def receive = receiveCommand

  override def receiveCommand: Receive = acceptingEvents

  override val receiveRecover: Receive = {

    case SnapshotOffer(metadata, offset: Long) =>
      log.debug(s"[$persistenceId] snapshot offer - lastProcessedOffset $offset")
      lastProcessedOffset = offset

    case _: RecoveryCompleted =>
      log.debug(s"[$persistenceId] recovery completed - lastProcessedOffset $lastProcessedOffset")
      recoveryCompleted()

    case unknown => log.debug(s"Unknown message on recovery: $unknown")
  }

  def saveCurrentOffset(offset: Long): Unit = {
    saveSnapshot(offset)
    // switch behavior but preserve previous Receive function so we can properly unbecome
    context.become(waitSnapshotConfirmation, discardOld = false)
  }

  def waitSnapshotConfirmation: Receive = {
    case SaveSnapshotSuccess(_) =>
      log.debug(s"[$persistenceId] snapshot saved")
      context.unbecome()
      unstashAll() // resume consuming DomainEvents

    case SaveSnapshotFailure(_, e) =>
      // can't do much in case of failure. Better to restart ProjectionActor
      throw e

    // stash any other msg
    case _ => stash()
  }
}
