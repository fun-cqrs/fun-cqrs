package lottery.domain.model

import java.time.OffsetDateTime
import java.util.UUID

import io.funcqrs._
import io.funcqrs.behavior.Types
import io.funcqrs.behavior._

import scala.util.Random

sealed trait Lottery {
  def id: LotteryId
}

case class EmptyLottery(id: LotteryId) extends Lottery {

  /**
    * Action: reject Run command if has no participants
    * Only applicable when list of participants is empty
    */
  def canNotRunWithoutParticipants =
    Lottery.actions
      .rejectCommand {
        // can't run if there is no participants
        case Run => new IllegalArgumentException("Lottery has no participants")
      }

  /**
    * Action: add a participant
    * Applicable as long as we don't have a winner
    */
  def acceptParticipants =
    Lottery.actions
      .handleCommand { cmd: AddParticipant =>
        ParticipantAdded(cmd.name, id)
      }
      .handleEvent { evt: ParticipantAdded =>
        NonEmptyLottery(
          participants = List(evt.name),
          id           = id
        )
      }
}

case class NonEmptyLottery(participants: List[String], id: LotteryId) extends Lottery {

  /**
    * Action: reject double booking. Can't add the same participant twice
    * Only applicable after adding at least one participant
    */
  def rejectDoubleBooking = {

    def hasParticipant(name: String) = participants.contains(name)

    Lottery.actions
      .rejectCommand {
        // can't add participant twice
        case AddParticipant(name) if hasParticipant(name) =>
          new IllegalArgumentException(s"""Participant $name already added!""")
      }
  }

  /**
    * Action: add a participant
    * Applicable as long as we don't have a winner
    */
  def acceptParticipants =
    Lottery.actions
      .handleCommand { cmd: AddParticipant =>
        ParticipantAdded(cmd.name, id)
      }
      .handleEvent { evt: ParticipantAdded =>
        copy(participants = evt.name :: participants)
      }

  /**
    * Action: remove participants (single or all)
    * Only applicable if Lottery has participants
    */
  def removeParticipants =
    Lottery.actions
    // removing participants (single or all) produce ParticipantRemoved events
      .handleCommand { cmd: RemoveParticipant =>
        ParticipantRemoved(cmd.name, id)
      }
      .handleCommand {
        // will produce a List[ParticipantRemoved]
        cmd: RemoveAllParticipants.type =>
          this.participants.map { name =>
            ParticipantRemoved(name, id)
          }
      }
      .handleEvent { evt: ParticipantRemoved =>
        val newParticipants = participants.filter(_ != evt.name)
        // NOTE: if last participant is removed, transition back to EmptyLottery
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
    Lottery.actions
      .handleCommand { cmd: Run.type =>
        val index  = Random.nextInt(participants.size)
        val winner = participants(index)
        WinnerSelected(winner, OffsetDateTime.now, id)
      }
      .handleEvent {
        // transition to end state on winner selection
        evt: WinnerSelected =>
          FinishedLottery(evt.winner, id)
      }
}

case class FinishedLottery(winner: String, id: LotteryId) extends Lottery {

  /**
    * Action: reject all
    * Applicable when a winner is selected. No new commands should be accepts.
    */
  def rejectAllCommands =
    Lottery.actions
      .rejectCommand {
        // no command can be accepted after having selected a winner
        case anyCommand =>
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

object Lottery extends Types[Lottery] {

  type Id      = LotteryId
  type Command = LotteryCommand
  type Event   = LotteryEvent

  // a tag for lottery, useful to query the event store later on
  val tag = Tags.aggregateTag("lottery")

  def behavior(lotteryId: LotteryId): Behavior[Lottery, LotteryCommand, LotteryEvent] =
    Behavior
    // defines how to construct a Lottery Aggregate
      .construct {

        actions
          .handleCommand { cmd: CreateLottery.type =>
            LotteryCreated(lotteryId)
          }
          .handleEvent { evt: LotteryCreated =>
            EmptyLottery(id = lotteryId)
          }
      }
      // defines how to update it
      .andThen {
        case lottery: EmptyLottery =>
          lottery.canNotRunWithoutParticipants ++
            lottery.acceptParticipants

        case lottery: NonEmptyLottery =>
          lottery.rejectDoubleBooking ++
            lottery.acceptParticipants ++
            lottery.removeParticipants ++
            lottery.runTheLottery

        case lottery: FinishedLottery => lottery.rejectAllCommands
      }
}

class LotteryHasAlreadyAWinner(msg: String) extends RuntimeException(msg)
