package lottery.domain.model

import java.time.OffsetDateTime
import java.util.UUID

import io.funcqrs._
import io.funcqrs.behavior._

import scala.util.Random

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
  def hasParticipants = participants.nonEmpty

  def hasParticipant(name: String) = participants.contains(name)

  def isNewParticipant(name: String) = !hasParticipant(name)

  import LotteryProtocol._

  def metadata(cmd: LotteryCommand) = {
    Lottery.metadata(id, cmd)
  }

  def rejectRunningWithoutParticipants =
    action[Lottery]
      .reject {
        // can't run if there is no participants
        case _: Run.type if this.hasNoParticipants =>
          new IllegalArgumentException("Lottery has no participants")
      }

  def rejectDoubleBooking =
    action[Lottery]
      .reject {
        // can't add participant twice
        case cmd: AddParticipant if this.hasParticipant(cmd.name) =>
          new IllegalArgumentException(s"Participant ${cmd.name} already added!")
      }

  // adding participant
  def acceptSubscriptions =
    actions[Lottery]
      .handleCommand {
        cmd: AddParticipant => ParticipantAdded(cmd.name, metadata(cmd))
      }
      .handleEvent {
        evt: ParticipantAdded => this.addParticipant(evt.name)
      }

  // running a lottery
  def runTheLottery =
    actions[Lottery]
      .handleCommand {
        cmd: Run.type => WinnerSelected(selectParticipant(), metadata(cmd))
      }
      .handleEvent {
        evt: WinnerSelected => this.copy(winner = Option(evt.winner))
      }

  def removingParticipants =
    actions[Lottery]
      // removing participants (single or all) produce ParticipantRemoved events
      .handleCommand {
        cmd: RemoveParticipant => ParticipantRemoved(cmd.name, metadata(cmd))
      }
      .handleCommand.manyEvents {
        // will produce a List[ParticipantRemoved]
        cmd: RemoveAllParticipants.type =>
          this.participants.map { name => ParticipantRemoved(name, metadata(cmd)) }
      }
      .handleEvent {
        evt: ParticipantRemoved => this.removeParticipant(evt.name)
      }

  def rejectAllCommands = {
    action[Lottery]
      .reject {
        // no command can be accepted after having selected a winner
        case anyCommand if this.hasWinner =>
          new LotteryHasAlreadyAWinner(s"Lottery has already a winner and the winner is ${winner.get}")
      }
  }
}

// end::lottery-aggregate[]

// tag::lottery-id[]
case class LotteryId(value: String) extends AggregateId

// end::lottery-id[]

object LotteryId {
  /** build a LotteryId from a String */
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

  case object RemoveAllParticipants extends LotteryCommand

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

  def metadata(lotteryId: LotteryId, cmd: LotteryCommand) = {
    LotteryMetadata(lotteryId, cmd.id, tags = Set(tag))
  }

  def createLottery(lotteryId: LotteryId) =
    actions[Lottery]
      .handleCommand {
        cmd: CreateLottery => LotteryCreated(cmd.name, metadata(lotteryId, cmd))
      }
      .handleEvent {
        evt: LotteryCreated => Lottery(name = evt.name, id = lotteryId)
      }

  def behavior(lotteryId: LotteryId): Behavior[Lottery] = {

    case Uninitialized(id) => createLottery(id)

    case Initialized(lottery) if lottery.hasWinner => lottery.rejectAllCommands

    case Initialized(lottery) if lottery.hasNoParticipants =>
      lottery.rejectRunningWithoutParticipants ++
        lottery.acceptSubscriptions

    case Initialized(lottery) if lottery.hasParticipants =>
      lottery.rejectDoubleBooking ++
        lottery.acceptSubscriptions ++
        lottery.removingParticipants ++
        lottery.runTheLottery
  }
}

class LotteryHasAlreadyAWinner(msg: String) extends RuntimeException(msg)

// end::lottery-behavior[]