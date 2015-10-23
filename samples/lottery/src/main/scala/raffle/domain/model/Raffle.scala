package raffle.domain.model

import java.time.OffsetDateTime
import funcqrs.json
import funcqrs.json.TypedJson
import TypedJson.{TypeHintFormat, _}
import scala.collection.immutable
import io.strongtyped.funcqrs._
import io.strongtyped.funcqrs.dsl.BehaviorDsl._
import TypedJson.{TypeHintFormat, _}
import play.api.libs.json.Json

import scala.concurrent.{Future, ExecutionContext}
import scala.util.Random

// tag::prod[]
case class Raffle(name: String, participants: List[String] = List(),
                  winner: Option[String] = None,
                  id: RaffleId) extends Aggregate {

  type Id = RaffleId
  type Protocol = RaffleProtocol.type

  def addParticipant(name: String): Raffle =
    copy(participants = participants :+ name)

  def selectParticipant(): String = {
    val index = Random.nextInt(participants.size)
    participants(index)
  }

  def hasWinner = winner.isDefined

  def hasNoParticipants = participants.isEmpty

}

case class RaffleId(value: String) extends AggregateID

// end::prod[]

object RaffleId {

  implicit val format = Json.format[RaffleId]

  def fromString(aggregateId: String): RaffleId = {
    RaffleId(aggregateId)
  }
}

object RaffleProtocol extends ProtocolDef {

  case class RaffleMetadata(aggregateId: RaffleId,
                            commandId: CommandId,
                            eventId: EventId = EventId(),
                            date: OffsetDateTime = OffsetDateTime.now(),
                            tags: Set[Tag] = Set()) extends Metadata with JavaTime {

    type Id = RaffleId
  }


  sealed trait RaffleCommand extends ProtocolCommand

  // Creation Command
  case class CreateRaffle(name: String) extends RaffleCommand

  case class AddParticipant(name: String) extends RaffleCommand

  case object Run extends RaffleCommand

  sealed trait RaffleEvent extends ProtocolEvent with MetadataFacet[RaffleMetadata]

  case class RaffleCreated(name: String,
                           metadata: RaffleMetadata) extends RaffleEvent

  sealed trait RaffleUpdateEvent extends RaffleEvent

  // Update Events
  case class ParticipantAdded(name: String, metadata: RaffleMetadata) extends RaffleUpdateEvent

  case class WinnerSelected(winner: String, metadata: RaffleMetadata) extends RaffleUpdateEvent


  // play-json formats for commands
  implicit val commandsFormat = {
    TypeHintFormat[RaffleCommand](
      Json.format[CreateRaffle].withTypeHint("Raffle.Create"),
      Json.format[AddParticipant].withTypeHint("Raffle.AddParticipant"),
      hintedObject(Run, "Raffle.Run")
    )
  }

}


object Raffle {

  val tag = Tags.aggregateTag("Raffle")

  def behavior(id: RaffleId): Behavior[Raffle] = behaviorImpl(id)

  private def behaviorImpl(id: RaffleId): Behavior[Raffle] = {

    import RaffleProtocol._

    def metadata(id: RaffleId, cmd: RaffleCommand) = {
      RaffleMetadata(id, cmd.id, tags = Set(tag))
    }

    behaviorFor[Raffle]
      .whenConstructing { it =>

      it.processesCommands {
        case cmd: CreateRaffle => RaffleCreated(cmd.name, metadata(id, cmd))
      }

      it.acceptsEvents {
        case evt: RaffleCreated => Raffle(name = evt.name, id = id)
      }

    }.whenUpdating { it =>

      it.processesCommands {
        case (raffle, _) if raffle.hasWinner                     => new CommandException("Raffle has already a winner")
        case (raffle, cmd: Run.type) if raffle.hasNoParticipants => new CommandException("Raffle has no participants")

        case (_, cmd: AddParticipant) => ParticipantAdded(cmd.name, metadata(id, cmd))
        case (raffle, cmd: Run.type)  => WinnerSelected(raffle.selectParticipant(), metadata(id, cmd))
      }

      it.acceptsEvents {
        case (raffle, evt: ParticipantAdded) => raffle.addParticipant(evt.name)
        case (raffle, evt: WinnerSelected)   => raffle.copy(winner = Option(evt.winner))
      }
    }
  }
}