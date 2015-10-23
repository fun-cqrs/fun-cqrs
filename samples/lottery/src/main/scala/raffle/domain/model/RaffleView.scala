package raffle.domain.model

import play.api.libs.json.Json

case class RaffleView(name: String, participants: List[String] = List(), winner: Option[String] = None, id: RaffleId)

object RaffleView {

  implicit val format = Json.writes[RaffleView]
}
