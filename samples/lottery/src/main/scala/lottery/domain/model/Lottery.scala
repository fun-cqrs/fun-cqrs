package lottery.domain.model

import java.time.OffsetDateTime

import funcqrs.json.TypedJson.{ TypeHintFormat, _ }
import io.strongtyped.funcqrs._
import play.api.libs.json.Json

import scala.util.Random

case class Lottery(name: String, participants: List[String] = List(),
                   winner: Option[String] = None,
                   id: LotteryId) extends AggregateLike {

  type Id = LotteryId
  type Protocol = LotteryProtocol.type

  def addParticipant(name: String): Lottery =
    copy(participants = participants :+ name)

  def removeParticipant(name: String): Lottery =
    copy(participants = participants.filter(_ != name))

  def selectParticipant(): String = {
    val index = Random.nextInt(participants.size)
    participants(index)
  }

  def hasWinner = winner.isDefined

  def hasNoParticipants = participants.isEmpty

  def hasParticipant(name: String) = participants.contains(name)
}

case class LotteryId(value: String) extends AggregateID

object LotteryId {

  implicit val format = Json.format[LotteryId]

  def fromString(aggregateId: String): LotteryId = {
    LotteryId(aggregateId)
  }
}

object LotteryProtocol extends ProtocolLike {

  case class LotteryMetadata(aggregateId: LotteryId,
                             commandId: CommandId,
                             eventId: EventId = EventId(),
                             date: OffsetDateTime = OffsetDateTime.now(),
                             tags: Set[Tag] = Set()) extends Metadata with JavaTime {

    type Id = LotteryId
  }

  sealed trait LotteryCommand extends ProtocolCommand

  // Creation Command
  case class CreateLottery(name: String) extends LotteryCommand

  case class AddParticipant(name: String) extends LotteryCommand

  case class RemoveParticipant(name: String) extends LotteryCommand

  case object Run extends LotteryCommand

  sealed trait LotteryEvent extends ProtocolEvent with MetadataFacet[LotteryMetadata]

  case class LotteryCreated(name: String,
                            metadata: LotteryMetadata) extends LotteryEvent

  sealed trait LotteryUpdateEvent extends LotteryEvent

  // Update Events
  case class ParticipantAdded(name: String, metadata: LotteryMetadata) extends LotteryUpdateEvent

  case class ParticipantRemoved(name: String, metadata: LotteryMetadata) extends LotteryUpdateEvent

  case class WinnerSelected(winner: String, metadata: LotteryMetadata) extends LotteryUpdateEvent

  // play-json formats for commands
  implicit val commandsFormat = {
    TypeHintFormat[LotteryCommand](
      Json.format[CreateLottery].withTypeHint("Lottery.Create"),
      Json.format[AddParticipant].withTypeHint("Lottery.AddParticipant"),
      Json.format[RemoveParticipant].withTypeHint("Lottery.RemoveParticipant"),
      hintedObject(Run, "Lottery.Run")
    )
  }

}

object Lottery {

  val tag = Tags.aggregateTag("Lottery")

  def behavior(id: LotteryId): Behavior[Lottery] = behaviorImpl(id)

  private def behaviorImpl(id: LotteryId): Behavior[Lottery] = {

    import LotteryProtocol._

    def metadata(id: LotteryId, cmd: LotteryCommand) = {
      LotteryMetadata(id, cmd.id, tags = Set(tag))
    }

    val lotteryBehaviorDsl = new io.strongtyped.funcqrs.dsl.BehaviorDsl[Lottery]

    import lotteryBehaviorDsl.behaviorBuilder._

    whenConstructing { it =>
      it processesCommands {
        case cmd: CreateLottery =>
          LotteryCreated(cmd.name, metadata(id, cmd))

      } acceptsEvents {
        case evt: LotteryCreated =>
          Lottery(name = evt.name, id = id)
      }
    } whenUpdating { it =>
      it processesCommands {
        case (lottery, cmd) if lottery.hasWinner =>
          new CommandException("Lottery has already a winner")

        case (lottery, cmd: Run.type) if lottery.hasNoParticipants =>
          new CommandException("Lottery has no participants")

        case (lottery, cmd: AddParticipant) if lottery.hasParticipant(cmd.name) =>
          new IllegalArgumentException(s"Participant ${cmd.name} already added!")

        case (lottery, cmd: AddParticipant) =>
          ParticipantAdded(cmd.name, metadata(id, cmd))

        case (_, cmd: RemoveParticipant) =>
          ParticipantRemoved(cmd.name, metadata(id, cmd))

        case (lottery, cmd: Run.type) =>
          WinnerSelected(lottery.selectParticipant(), metadata(id, cmd))

      } acceptsEvents {
        case (lottery, evt: ParticipantAdded) =>
          lottery.addParticipant(evt.name)

        case (lottery, evt: ParticipantRemoved) =>
          lottery.removeParticipant(evt.name)

        case (lottery, evt: WinnerSelected) =>
          lottery.copy(winner = Option(evt.winner))
      }
    }
  }
}