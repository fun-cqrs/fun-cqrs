package lottery.domain.model

import java.time.OffsetDateTime

//tag::lottery-view[]
case class LotteryView(
    name: String,
    participants: List[LotteryView.Participant] = List(),
    winner: Option[String] = None,
    runDate: Option[OffsetDateTime] = None,
    id: LotteryId
) {

  override def toString: String = {
    val participantsString =
      participants.map { p => s"name: ${p.name} - date: ${p.date}" }.mkString(" | ")
    s"""
       |LotteryView
       |  name: $name
       |  participants: $participantsString
       |  winner: ${winner.getOrElse("N/A")}
       |  runDate: ${runDate.map(_.toString).getOrElse("")}
       |  id: $id
     """.stripMargin
  }
}

object LotteryView {
  case class Participant(name: String, date: OffsetDateTime)
}
//end::lottery-view[]