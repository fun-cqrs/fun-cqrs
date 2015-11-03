package lottery.domain.model

import java.time.OffsetDateTime

import funcqrs.json.TypedJson.{ TypeHintFormat, _ }
import io.strongtyped.funcqrs._
import io.strongtyped.funcqrs.dsl.BehaviorDsl._
import play.api.libs.json.Json

import scala.concurrent.{ Future, ExecutionContext }
import scala.util.{ Try, Success, Failure, Random }

case class Lottery(name: String, participants: List[String] = List(),
                   winner: Option[String] = None,
                   id: LotteryId) extends Aggregate {

  type Id = LotteryId
  type Protocol = LotteryProtocol.type

  import LotteryProtocol._

  def addParticipant(name: String): Lottery =
    copy(participants = participants :+ name)

  def removeParticipant(name: String): Lottery =
    copy(participants = participants.filter(_ != name))

  def pickParticipantRandomly(): Try[String] = {
    // dangerous!! can blow up
    Try(participants(Random.nextInt(participants.size)))
  }

  def hasWinner = winner.isDefined

  def selectWinner(name:String): Lottery = {
    copy(winner = Option(name))
  }

  def hasNoParticipants = participants.isEmpty

  def hasParticipant(name: String) = participants.contains(name)
}

case class LotteryId(value: String) extends AggregateID

object LotteryId {

  implicit val format = Json.format[LotteryId]

  def fromString(aggregateId: String): LotteryId = LotteryId(aggregateId)
}

object LotteryProtocol extends ProtocolDef {

  case class LotteryMetadata(aggregateId: LotteryId,
                             commandId: CommandId,
                             eventId: EventId = EventId(),
                             date: OffsetDateTime = OffsetDateTime.now(),
                             tags: Set[Tag] = Set()) extends Metadata with JavaTime {

    type Id = LotteryId
  }

  // COMMANDS
  sealed trait LotteryCommand extends ProtocolCommand

  sealed trait LotteryEvent extends ProtocolEvent with MetadataFacet[LotteryMetadata]
  sealed trait LotteryUpdateEvent extends LotteryEvent

  // play-json formats for commands
//  implicit val commandsFormat = {
//    TypeHintFormat[LotteryCommand](
//      Json.format[CreateLottery].withTypeHint("Lottery.Create"),
//      Json.format[AddParticipant].withTypeHint("Lottery.AddParticipant"),
//      Json.format[RemoveParticipant].withTypeHint("Lottery.RemoveParticipant"),
//      hintedObject(Run, "Lottery.Run")
//    )
//  }

}

object Lottery {

  val tag = Tags.aggregateTag("Lottery")

  import io.strongtyped.funcqrs.dsl.BehaviorDsl._

  def behavior(id: LotteryId): Behavior[Lottery] = {
    import LotteryProtocol._

    def metadata(id: LotteryId, cmd: LotteryCommand) = {
      LotteryMetadata(id, cmd.id, tags = Set(tag))
    }

    Behavior.empty
  }

}