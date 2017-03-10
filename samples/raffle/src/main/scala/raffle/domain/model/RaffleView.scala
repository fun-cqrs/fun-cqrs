package raffle.domain.model

import java.time.OffsetDateTime

case class RaffleView(
    participants: List[RaffleView.Participant] = List(),
    winner: Option[String]                     = None,
    runDate: Option[OffsetDateTime]            = None,
    id: RaffleId
) {

  override def toString: String = {
    val participantsString =
      participants.map(_.name).mkString(" | ")
    s"""
       |RaffleView
       |  participants: $participantsString
       |  winner: ${winner.getOrElse("N/A")}
       |  runDate: ${runDate.map(_.toString).getOrElse("")}
       |  id: $id
     """.stripMargin
  }
}

object RaffleView {
  case class Participant(name: String)
}
