package lottery.domain.model

import java.time.OffsetDateTime
import java.util.UUID
import io.funcqrs._
import io.funcqrs.behavior.Behavior
import io.funcqrs.dsl.BindingDsl.api._

import scala.util.{ Failure, Random, Success, Try }

// tag::lottery-aggregate[]
case class Lottery(
    name: String,
    participants: List[String] = List(),
    winner: Option[String] = None,
    id: LotteryId
) extends AggregateLike {

  type Id = LotteryId // #<1>
  type Protocol = LotteryProtocol.type // #<2>

  def addParticipant(name: String): Lottery = {
    copy(participants = participants :+ name)
  }

  def removeParticipant(name: String): Lottery = {
    copy(participants = participants.filter(_ != name))
  }

  def selectParticipant(): String = {
    val index = Random.nextInt(participants.size)
    participants(index)
  }

  def hasWinner = winner.isDefined

  def hasNoParticipants = participants.isEmpty

  def hasParticipant(name: String) = participants.contains(name)
  def isNewParticipant(name: String) = !hasParticipant(name)
}
// end::lottery-aggregate[]

// tag::lottery-id[]
case class LotteryId(value: String) extends AggregateId
// end::lottery-id[]

object LotteryId {
  /** build a LotterId from a String */
  def fromString(aggregateId: String): LotteryId = LotteryId(aggregateId)

  /** generate a random LotteryId */
  def generate(): LotteryId = LotteryId(UUID.randomUUID().toString)
}

// tag::lottery-protocol[]
object LotteryProtocol extends ProtocolLike { // #<1>

  // Commands ============================================================
  sealed trait LotteryCommand extends ProtocolCommand // #<2>

  // Creation Command
  case class CreateLottery(name: String) extends LotteryCommand
  // Update Commands
  case class AddParticipant(name: String) extends LotteryCommand
  case class RemoveParticipant(name: String) extends LotteryCommand
  case object Reset extends LotteryCommand
  case object Run extends LotteryCommand

  // Events ============================================================
  sealed trait LotteryEvent extends ProtocolEvent with MetadataFacet[LotteryMetadata] // #<3>

  case class LotteryMetadata( // #<4>
      aggregateId: LotteryId, // #<5>
      commandId: CommandId, // #<6>
      eventId: EventId = EventId(), // #<7>
      date: OffsetDateTime = OffsetDateTime.now(), // #<8>
      tags: Set[Tag] = Set() // #<9>
  ) extends Metadata with JavaTime {
    type Id = LotteryId
  }

  // Creation Event
  case class LotteryCreated(name: String, metadata: LotteryMetadata) extends LotteryEvent
  // Update Events
  sealed trait LotteryUpdateEvent extends LotteryEvent
  case class ParticipantAdded(name: String, metadata: LotteryMetadata) extends LotteryUpdateEvent
  case class ParticipantRemoved(name: String, metadata: LotteryMetadata) extends LotteryUpdateEvent
  case class WinnerSelected(winner: String, metadata: LotteryMetadata) extends LotteryUpdateEvent
}
// end::lottery-protocol[]

// tag::lottery-behavior[]
object Lottery {

  // import the protocol to have access to Commands and Events
  import LotteryProtocol._

  // a tag for lottery, useful to query the event store later on
  val tag = Tags.aggregateTag("lottery")

  def behavior(lotteryId: LotteryId): Behavior[Lottery] = {

    // convenient method for building LotteryMetatadata
    def metadata(cmd: LotteryCommand) = {
      LotteryMetadata(lotteryId, cmd.id, tags = Set(tag))
    }

    // start to describe Lottery Behavior
    describe[Lottery]

      .whenCreating {
        // creational command and event
        handler {
          cmd: CreateLottery => LotteryCreated(cmd.name, metadata(cmd))
        } listener {
          evt => Lottery(name = evt.name, id = lotteryId)
        }
      }

      // Some guard clauses. 
      // Commands bellow won't generate events, but be reject with an exception
      .whenUpdating { lottery =>
        reject {
          // no command can be accepted after having selected a winner
          case anyCommand if lottery.hasWinner =>
            new IllegalArgumentException("Lottery has already a winner!")
        }
      }
      .whenUpdating { lottery =>
        reject {
          // can't run if there is no participants
          case _: Run.type if lottery.hasNoParticipants =>
            new IllegalArgumentException("Lottery has no participants")
        }
      }
      .whenUpdating { lottery =>
        reject {
          // can't add participant twice
          case cmd: AddParticipant if lottery.hasParticipant(cmd.name) =>
            new IllegalArgumentException(s"Participant ${cmd.name} already added!")
        }
      }

      // Logic to update a lottery. Commands bellow will generate events
      .whenUpdating { lottery =>
        // Select a winner when run!
        handler {
          cmd: Run.type => WinnerSelected(lottery.selectParticipant(), metadata(cmd))
        } listener {
          evt => lottery.copy(winner = Option(evt.winner))
        }

      }
      .whenUpdating { lottery =>
        handler {
          cmd: AddParticipant => ParticipantAdded(cmd.name, metadata(cmd))
        } listener {
          evt => lottery.addParticipant(evt.name)
        }

      }
      .whenUpdating { lottery =>
        handler {
          cmd: RemoveParticipant => ParticipantRemoved(cmd.name, metadata(cmd))
        } listener {
          evt => lottery.removeParticipant(evt.name)
        }

      }
      .whenUpdating { lottery =>
        // Reset is a special case as we need to generate many events
        handler.manyEvents {
          // will produce a List[ParticipantRemoved]
          cmd: Reset.type =>
            lottery.participants.map { name =>
              ParticipantRemoved(name, metadata(cmd))
            }

        } listener {
          // MUST be a PartialFunciton when command handler generates a List of events
          case evt: ParticipantRemoved => lottery.removeParticipant(evt.name)
        }
      }
  }
}
// end::lottery-behavior[]