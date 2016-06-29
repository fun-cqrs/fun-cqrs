package lottery.domain.model

import java.time.OffsetDateTime
import java.util.UUID

import io.funcqrs._
import io.funcqrs.behavior._

import scala.util.Random

sealed trait Lottery extends AggregateLike {
  type Id       = LotteryId
  type Protocol = LotteryProtocol.type
}

case class EmptyLottery(id: LotteryId) extends Lottery {

  import LotteryProtocol._

  /**
   * Action: reject Run command if has no participants
   * Only applicable when list of participants is empty
   */
  def canNotRunWithoutParticipants =
    action[Lottery]
      .rejectCommand {
        // can't run if there is no participants
        case _: Run.type  =>
          new IllegalArgumentException("Lottery has no participants")
      }


  /**
   * Action: add a participant
   * Applicable as long as we don't have a winner
   */
  def acceptParticipants =
    actions[Lottery]
      .handleCommand {
        cmd: AddParticipant => ParticipantAdded(cmd.name, id)
      }
      .handleEvent {
        evt: ParticipantAdded =>
          NonEmptyLottery(
            participants = List(evt.name),
            id = id
          )
      }
}

case class NonEmptyLottery(participants: List[String], id: LotteryId) extends Lottery {

  import LotteryProtocol._


  /**
   * Action: reject double booking. Can't add the same participant twice
   * Only applicable after adding at least one participant
   */
  def rejectDoubleBooking = {

    def hasParticipant(name: String) = participants.contains(name)

    action[Lottery]
      .rejectCommand {
        // can't add participant twice
        case cmd: AddParticipant if hasParticipant(cmd.name) =>
          new IllegalArgumentException(s"Participant ${cmd.name} already added!")
      }
  }

  /**
   * Action: add a participant
   * Applicable as long as we don't have a winner
   */
  def acceptParticipants =
    actions[Lottery]
      .handleCommand {
        cmd: AddParticipant => ParticipantAdded(cmd.name, id)
      }
      .handleEvent {
        evt: ParticipantAdded => copy(participants = participants :+ evt.name)
      }

  /**
   * Action: remove participants (single or all)
   * Only applicable if Lottery has participants
   */
  def removeParticipants =
    actions[Lottery]
      // removing participants (single or all) produce ParticipantRemoved events
      .handleCommand {
        cmd: RemoveParticipant => ParticipantRemoved(cmd.name, id)
      }
      .handleCommand {
        // will produce a List[ParticipantRemoved]
        cmd: RemoveAllParticipants.type =>
          this.participants.map { name => ParticipantRemoved(name, id) }
      }
      .handleEvent {
        evt: ParticipantRemoved =>
          val newParticipants = participants.filter(_ != evt.name)
          // if last participant is removed, transition to EmptyLottery
          if (newParticipants.isEmpty)
            EmptyLottery(id)
          else
            copy(participants = newParticipants)
      }
  /**
   * Action: run the lottery
   * Only applicable if it has at least one participant
   */
  def runTheLottery =
  actions[Lottery]
    .handleCommand {
      cmd: Run.type =>
        val index = Random.nextInt(participants.size)
        val winner = participants(index)
        WinnerSelected(winner, OffsetDateTime.now, id)
    }
    .handleEvent {
      // transition to end state on winner selection
      evt: WinnerSelected => FinishedLottery(evt.winner, id)
    }
}

case class FinishedLottery(winner: String, id: LotteryId) extends Lottery {

  /**
   * Action: reject all
   * Applicable when a winner is selected. No new commands should be accepts.
   */
  def rejectAllCommands =
    action[Lottery]
      .rejectCommand {
        // no command can be accepted after having selected a winner
        case anyCommand  =>
          new LotteryHasAlreadyAWinner(s"Lottery has already a winner and the winner is $winner")
      }
}

/** Defines the a type-safe ID for Lottery Aggregate */
case class LotteryId(value: String) extends AggregateId


object LotteryId {

  /** build a LotteryId from a String */
  def fromString(aggregateId: String): LotteryId = LotteryId(aggregateId)

  /** generate a random LotteryId */
  def generate(): LotteryId = LotteryId(UUID.randomUUID().toString)
}


/** Defines the Lottery Protocol, all Commands it may receive and Events it may emit */
object LotteryProtocol extends ProtocolLike {

  // Commands ============================================================
  sealed trait LotteryCommand extends ProtocolCommand
  // Creation Command
  case class CreateLottery(name: String) extends LotteryCommand

  // Update Commands
  case class AddParticipant(name: String) extends LotteryCommand

  case class RemoveParticipant(name: String) extends LotteryCommand

  case object RemoveAllParticipants extends LotteryCommand

  case object Run extends LotteryCommand

  // Events ============================================================
  sealed trait LotteryEvent extends ProtocolEvent {
    def lotteryId: LotteryId
  }

  // Creation Event
  case class LotteryCreated(name: String, lotteryId: LotteryId) extends LotteryEvent
  // Update Events
  sealed trait LotteryUpdateEvent extends LotteryEvent
  case class ParticipantAdded(name: String, lotteryId: LotteryId) extends LotteryUpdateEvent
  case class ParticipantRemoved(name: String, lotteryId: LotteryId) extends LotteryUpdateEvent
  case class WinnerSelected(winner: String, date: OffsetDateTime, lotteryId: LotteryId) extends LotteryUpdateEvent

}

object Lottery {

  // import the protocol to have access to Commands and Events
  import LotteryProtocol._

  // a tag for lottery, useful to query the event store later on
  val tag = Tags.aggregateTag("lottery")

  def createLottery(lotteryId: LotteryId) =
    actions[Lottery]
      .handleCommand {
        cmd: CreateLottery => LotteryCreated(cmd.name, lotteryId)
      }
      .handleEvent {
        evt: LotteryCreated => EmptyLottery(id = lotteryId)
      }

  def behavior(lotteryId: LotteryId): Behavior[Lottery] =
    Behavior {
      createLottery(lotteryId)
    } {
      case lottery: EmptyLottery =>
        lottery.canNotRunWithoutParticipants ++
          lottery.acceptParticipants

      case lottery: NonEmptyLottery=>
        lottery.rejectDoubleBooking ++
          lottery.acceptParticipants ++
          lottery.removeParticipants ++
          lottery.runTheLottery

      case lottery: FinishedLottery => lottery.rejectAllCommands
    }
}

class LotteryHasAlreadyAWinner(msg: String) extends RuntimeException(msg)
