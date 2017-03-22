package raffle.domain.model

import java.time.OffsetDateTime

// Events ============================================================
sealed trait RaffleEvent {
  def lotteryId: RaffleId
}

// Creation Event
case class RaffleCreated(lotteryId: RaffleId) extends RaffleEvent
// Update Events
sealed trait RaffleUpdateEvent extends RaffleEvent
case class ParticipantAdded(name: String, lotteryId: RaffleId) extends RaffleUpdateEvent
case class ParticipantRemoved(name: String, lotteryId: RaffleId) extends RaffleUpdateEvent
case class WinnerSelected(winner: String, date: OffsetDateTime, lotteryId: RaffleId) extends RaffleUpdateEvent
