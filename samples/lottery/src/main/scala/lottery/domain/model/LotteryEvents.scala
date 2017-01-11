package lottery.domain.model

import java.time.OffsetDateTime


// Events ============================================================
sealed trait LotteryEvent {
  def lotteryId: LotteryId
}


// Creation Event
case class LotteryCreated(lotteryId: LotteryId) extends LotteryEvent
// Update Events
sealed trait LotteryUpdateEvent extends LotteryEvent
case class ParticipantAdded(name: String, lotteryId: LotteryId) extends LotteryUpdateEvent
case class ParticipantRemoved(name: String, lotteryId: LotteryId) extends LotteryUpdateEvent
case class WinnerSelected(winner: String, date: OffsetDateTime, lotteryId: LotteryId) extends LotteryUpdateEvent
