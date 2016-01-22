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
) extends AggregateLike { // #<1>

  type Id = LotteryId // #<2>
  type Protocol = LotteryProtocol.type // #<3>

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

  case class LotteryMetadata(
      aggregateId: LotteryId,
      commandId: CommandId,
      eventId: EventId = EventId(),
      date: OffsetDateTime = OffsetDateTime.now(),
      tags: Set[Tag] = Set()
  ) extends Metadata with JavaTime {
    type Id = LotteryId
  }

  sealed trait LotteryCommand extends ProtocolCommand // #<2>

  // Creation Commands
  case class CreateLottery(name: String) extends LotteryCommand
  // Update Commands
  case class AddParticipant(name: String) extends LotteryCommand
  case class RemoveParticipant(name: String) extends LotteryCommand
  case object Reset extends LotteryCommand
  case object Run extends LotteryCommand

  // base Event
  sealed trait LotteryEvent extends ProtocolEvent with MetadataFacet[LotteryMetadata]

  case class LotteryCreated(name: String, metadata: LotteryMetadata) extends LotteryEvent

  // Update Events
  sealed trait LotteryUpdateEvent extends LotteryEvent
  case class ParticipantAdded(name: String, metadata: LotteryMetadata)
    extends LotteryUpdateEvent
  case class ParticipantRemoved(name: String, metadata: LotteryMetadata)
    extends LotteryUpdateEvent
  case class WinnerSelected(winner: String, metadata: LotteryMetadata)
    extends LotteryUpdateEvent
}
// end::lottery-protocol[]

object Lottery {

  import LotteryProtocol._

  val tag = Tags.aggregateTag("lottery")
  def metadata(id: LotteryId, cmd: LotteryCommand) = {
    LotteryMetadata(id, cmd.id, tags = Set(tag))
  }

  def behavior(lotteryId: LotteryId): Behavior[Lottery] = {

    describe[Lottery]

      .whenCreating {
        // creational command and event
        handler { cmd: CreateLottery => LotteryCreated(cmd.name, metadata(lotteryId, cmd)) }
          .listener { evt => Lottery(name = evt.name, id = lotteryId) }

      }

      // some guard clauses 
      .whenUpdating { lottery =>

        rejectCommand {
          // no command accepted after running the lottery 
          case anyCommand if lottery.hasWinner => new IllegalArgumentException("Lottery has already a winner!")
        }
      }
      .whenUpdating { lottery =>
        rejectCommand {
          // can't run if there is no participants
          case _: Run.type if lottery.hasNoParticipants => new IllegalArgumentException("Lottery has no participants")
        }
      }

      // logic to update a lottery
      .whenUpdating { lottery =>

        // Select a winner when run!
        handler { cmd: Run.type => WinnerSelected(lottery.selectParticipant(), metadata(lotteryId, cmd)) }
          .listener { evt => lottery.copy(winner = Option(evt.winner)) }

      }
      .whenUpdating { lottery =>

        tryHandler { cmd: AddParticipant =>
          if (lottery.hasParticipant(cmd.name))
            Failure(new IllegalArgumentException(s"Participant ${cmd.name} already added!"))
          else
            Success(ParticipantAdded(cmd.name, metadata(lotteryId, cmd)))
        } listener { evt =>
          lottery.addParticipant(evt.name)
        }

      }
      .whenUpdating { lottery =>

        handler { cmd: RemoveParticipant => ParticipantRemoved(cmd.name, metadata(lotteryId, cmd)) }
          .listener { evt => lottery.removeParticipant(evt.name) }

      }
      .whenUpdating { lottery =>

        handler.manyEvents { cmd: Reset.type =>
          lottery.participants.map { name => ParticipantRemoved(name, metadata(lotteryId, cmd)) }
        } listener {
          case evt: ParticipantRemoved => lottery.removeParticipant(evt.name)
        }
      }

  }
}