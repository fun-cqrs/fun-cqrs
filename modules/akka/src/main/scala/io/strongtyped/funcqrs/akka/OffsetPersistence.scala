package io.strongtyped.funcqrs.akka

import akka.persistence.{ PersistentActor, RecoveryCompleted, SnapshotOffer }

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
trait PersistedOffsetDb extends OffsetPersistence {
  this: ProjectionActor =>

  def saveCurrentOffset(offset: Long): Unit

  /** Returns the current offset as persisted in DB */
  def readOffset: Future[Long]

  /** On preStart we read the offset from db and start the events streamins */
  override def preStart(): Unit = {
    import scala.concurrent.ExecutionContext.Implicits.global
    readOffset.map { offset =>
      currentOffset = offset
      recoveryCompleted()
    }
  }
}

/** Persist Offset as snapshot in akka-persistence
  *
  * This implementation is a quick win for those that simply want to persist the offset without caring about
  * the persistence layer.
  *
  * However, the drawback is that most (if not all) akka-persistence snapshot plugins will
  * save it as binary data which make it difficult to inspect the DB to get to know the last processed event.
  */
trait PersistedOffsetAkka extends OffsetPersistence with PersistentActor {
  self: ProjectionActor =>

  def persistenceId: String = name

  override def receive = receiveCommand
  override def receiveCommand: Receive = acceptingEvents

  override val receiveRecover: Receive = {

    case SnapshotOffer(metadata, offset: Long) =>
      log.debug(s"snapshot offer $offset")
      currentOffset = offset

    case _: RecoveryCompleted =>
      log.debug(s"Recovery completed for ProjectionActor $name")
      recoveryCompleted()

    case unknown => log.debug(s"Unknown message on recovery: $unknown")
  }

  def saveCurrentOffset(offset: Long): Unit = saveSnapshot(currentOffset)
}
