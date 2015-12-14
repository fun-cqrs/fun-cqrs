package lottery.domain.model

import java.time.OffsetDateTime
import java.util.UUID

import funcqrs.json.TypedJson.{ TypeHintFormat, _ }
import io.funcqrs._
import io.funcqrs.dsl.BindingDsl
import play.api.libs.json.Json

import scala.util.{ Failure, Random, Success, Try }

case class Lottery(name: String, participants: List[String] = List(),
                   winner: Option[String] = None,
                   id: LotteryId) extends AggregateLike {

  type Id = LotteryId
  type Protocol = LotteryProtocol.type

  def addParticipant(name: String): Lottery = {
    copy(participants = participants :+ name)
  }

  def removeParticipant(name: String): Lottery =
    copy(participants = participants.filter(_ != name))

  def selectParticipant(): Try[String] = {

    if (hasWinner) {
      Failure(new RuntimeException("Lottery has already a winner!"))
    } else if (hasNoParticipants) {
      Failure(new RuntimeException("Lottery has no participants"))
    } else {
      val index = Random.nextInt(participants.size)
      Try(participants(index))
    }
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

  def generate(): LotteryId = LotteryId(UUID.randomUUID().toString)
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

  case object Reset extends LotteryCommand

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

  import LotteryProtocol._

  val tag = Tags.aggregateTag("Lottery")

  def metadata(id: LotteryId, cmd: LotteryCommand) = {
    LotteryMetadata(id, cmd.id, tags = Set(tag))
  }

  def behavior(id: LotteryId): Behavior[Lottery] = behaviorImpl(id)

  private def behaviorImpl(id: LotteryId): Behavior[Lottery] = {

    import io.funcqrs.dsl.BindingDsl.api._

    behaviorFor[Lottery]
      .whenCreating {
        // creational command and event
        command { cmd: CreateLottery => LotteryCreated(cmd.name, metadata(id, cmd)) }
          .action { evt => Lottery(name = evt.name, id = id) }

        // updates
      } whenUpdating { lottery =>

        // Select a winner when run!
        command { cmd: Run.type =>
          lottery.selectParticipant().map { winner => WinnerSelected(winner, metadata(id, cmd)) }
        } action { evt =>
          lottery.copy(winner = Option(evt.winner))
        }

      } whenUpdating { lottery =>
        // add participant
        command { cmd: AddParticipant =>
          if (lottery.hasParticipant(cmd.name))
            Failure(new IllegalArgumentException(s"Participant ${cmd.name} already added!"))
          else
            Success(ParticipantAdded(cmd.name, Lottery.metadata(id, cmd)))
        } action { evt =>
          lottery.addParticipant(evt.name)
        }

      } whenUpdating { lottery =>
        command { cmd: RemoveParticipant => ParticipantRemoved(cmd.name, metadata(id, cmd)) }
          .action { evt => lottery.removeParticipant(evt.name) }

      } whenUpdating { lottery =>

        command.multipleEvents { cmd: Reset.type =>
          lottery.participants.map { name => ParticipantRemoved(name, metadata(id, cmd)) }
        } action { evt =>
          lottery.removeParticipant(evt.name)
        }
      }

  }
}