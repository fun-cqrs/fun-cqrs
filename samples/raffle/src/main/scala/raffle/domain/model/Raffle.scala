package raffle.domain.model

import java.time.OffsetDateTime
import java.util.UUID

import io.funcqrs._
import io.funcqrs.behavior.Types
import io.funcqrs.behavior._
import io.funcqrs.behavior.handlers._

import scala.concurrent.Future
import scala.util.{ Random, Try }

sealed trait Raffle {
  def id: RaffleId
}

case class EmptyRaffle(id: RaffleId) extends Raffle {

  /**
    * Action: reject Run command if has no participants
    * Only applicable when list of participants is empty
    */
  def canNotRunWithoutParticipants =
    Raffle.actions
      .rejectCommand {
        // can't run if there is no participants
        case Run => new IllegalArgumentException("Raffle has no participants")
      }

  /**
    * Action: add a participant
    * Applicable as long as we don't have a winner
    */
  def acceptParticipants =
    Raffle.actions
      .commandHandler {
        OneEvent {
          case AddParticipant(name) => ParticipantAdded(name, id)
        }
      }
      .eventHandler {
        case ParticipantAdded(name, _) =>
          NonEmptyRaffle(
            participants = List(name),
            id           = id
          )
      }
}

case class NonEmptyRaffle(participants: List[String], id: RaffleId) extends Raffle {

  /**
    * Action: reject double booking. Can't add the same participant twice
    * Only applicable after adding at least one participant
    */
  def rejectDoubleBooking = {

    def hasParticipant(name: String) = participants.contains(name)

    Raffle.actions
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
    Raffle.actions
      .commandHandler {
        OneEvent {
          case AddParticipant(name) => ParticipantAdded(name, id)
        }
      }
      .eventHandler {
        case ParticipantAdded(name, _) => copy(participants = name :: participants)
      }

  /**
    * Action: remove participants (single or all)
    * Only applicable if Raffle has participants
    */
  def removeParticipants =
    Raffle.actions
    // removing participants (single or all) produce ParticipantRemoved events
      .commandHandler {
        OneEvent {
          case RemoveParticipant(name) => ParticipantRemoved(name, id)
        }
      }
      .commandHandler {
        ManyEvents {
          // will produce a List[ParticipantRemoved]
          case RemoveAllParticipants =>
            this.participants.map { name =>
              ParticipantRemoved(name, id)
            }
        }
      }
      .eventHandler {
        case ParticipantRemoved(name, _) =>
          val newParticipants = participants.filter(_ != name)
          // NOTE: if last participant is removed, transition back to EmptyRaffle
          if (newParticipants.isEmpty)
            EmptyRaffle(id)
          else
            copy(participants = newParticipants)
      }

  /**
    * Action: run the lottery
    * Only applicable if it has at least one participant
    */
  def runTheRaffle =
    Raffle.actions
      .commandHandler {
        OneEvent {
          case Run =>
            val index  = Random.nextInt(participants.size)
            val winner = participants(index)
            WinnerSelected(winner, OffsetDateTime.now, id)
        }
      }
      .eventHandler {
        // transition to end state on winner selection
        case evt: WinnerSelected => FinishedRaffle(evt.winner, id)
      }
}

case class FinishedRaffle(winner: String, id: RaffleId) extends Raffle {

  /**
    * Action: reject all
    * Applicable when a winner is selected. No new commands should be accepts.
    */
  def rejectAllCommands =
    Raffle.actions
      .rejectCommand {
        // no command can be accepted after having selected a winner
        case anyCommand =>
          new RaffleHasAlreadyAWinner(s"Raffle has already a winner and the winner is $winner")
      }
}

/** Defines the a type-safe ID for Raffle Aggregate */
case class RaffleId(value: String) extends AggregateId

object RaffleId {

  /** build a RaffleId from a String */
  def fromString(aggregateId: String): RaffleId = RaffleId(aggregateId)

  /** generate a random RaffleId */
  def generate(): RaffleId = RaffleId(UUID.randomUUID().toString)
}

object Raffle extends Types[Raffle] {

  type Id      = RaffleId
  type Command = RaffleCommand
  type Event   = RaffleEvent

  // a tag for raffle, useful to query the event store later on
  val tag = Tags.aggregateTag("raffle")

  def create(raffleId: RaffleId) = {
    actions
      .commandHandler {
        OneEvent { case CreateRaffle => RaffleCreated(raffleId) }
      }
      .eventHandler {
        case _: RaffleCreated => EmptyRaffle(id = raffleId)
      }
  }

  def behavior(raffleId: RaffleId): Behavior[Raffle, RaffleCommand, RaffleEvent] =
    Behavior
      .first { // defines how to construct a Raffle Aggregate
        create(raffleId)
      }
      // defines how to update it
      .andThen {
        case raffle: EmptyRaffle =>
          raffle.canNotRunWithoutParticipants ++
            raffle.acceptParticipants

        case raffle: NonEmptyRaffle =>
          raffle.rejectDoubleBooking ++
            raffle.acceptParticipants ++
            raffle.removeParticipants ++
            raffle.runTheRaffle

        case raffle: FinishedRaffle => raffle.rejectAllCommands
      }
}

class RaffleHasAlreadyAWinner(msg: String) extends RuntimeException(msg)
