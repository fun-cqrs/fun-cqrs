package io.funcqrs.config

import akka.Done
import akka.actor.{ Actor, ActorLogging, ActorSystem, Props }
import akka.persistence.{ PersistentActor, RecoveryCompleted, SnapshotOffer }
import akka.util.Timeout

import scala.concurrent.{ Future, Promise }
import scala.concurrent.duration._
import akka.pattern._
import io.funcqrs.akka.PersistedOffsetAkka.LastProcessedEventOffset

object AkkaOffsetPersistenceStrategy {

  def offsetAsLong(actorSystem: ActorSystem, projectionName: String, deleteOld: Boolean = true) = {
    new CustomOffsetPersistenceStrategy[Long] {

      implicit val timeout = Timeout(10.seconds)
      import scala.concurrent.ExecutionContext.Implicits.global
      val actor = actorSystem.actorOf(LongOffsetActor.props(projectionName, deleteOld))

      override def saveCurrentOffset(offset: Long): Future[Unit] =
        (actor ? LongOffsetActor.SaveOffset(offset)).map(_ => ())

      /** Returns the current offset as persisted in DB */
      override def readOffset: Future[Option[Long]] =
        (actor ? LongOffsetActor.GetOffset).mapTo[Option[Long]]

    }
  }
}

object LongOffsetActor {
  case object GetOffset
  case class SaveOffset(offset: Long)

  def props(name: String, deletePrevious: Boolean) = Props(new LongOffsetActor(name, deletePrevious))
}
class LongOffsetActor(name: String, deletePrevious: Boolean) extends Actor with PersistentActor with ActorLogging {

  var lastProcessedOffset: Option[Long] = None

  def persistenceId: String = name

  override def receiveCommand: Receive = {

    case LongOffsetActor.GetOffset          => sender() ! lastProcessedOffset
    case LongOffsetActor.SaveOffset(offset) => saveCurrentOffset(offset)
  }

  override val receiveRecover: Receive = {

    case SnapshotOffer(metadata, offset: Long) =>
      log.debug("[{}] snapshot offer - last processed event offset {}", persistenceId, offset)
      lastProcessedOffset = Some(offset)

    case LastProcessedEventOffset(offset) =>
      log.debug("[{}] - last processed event offset {}", persistenceId, offset)
      lastProcessedOffset = Option(offset)

    case _: RecoveryCompleted =>
      log.debug("[{}] recovery completed - last processed event offset {}", persistenceId, lastProcessedOffset)

    case unknown => log.debug("Unknown message on recovery: {}", unknown)

  }

  def saveCurrentOffset(offset: Long): Unit = {

    persist(LastProcessedEventOffset(offset)) { _ =>
      if (deletePrevious) {

        log.debug("Projection: {} - saving domain event offset {}", persistenceId, offset)

        val seqNrToDelete = lastSequenceNr - 1

        // delete old message if any, no need to wait
        if (seqNrToDelete > 0) {
          log.debug("Projection: {} - deleting previous projection event: {}", persistenceId, seqNrToDelete)
          deleteMessages(seqNrToDelete)
        }
      }

      lastProcessedOffset = Option(offset)
      sender() ! Done
    }

  }
}
