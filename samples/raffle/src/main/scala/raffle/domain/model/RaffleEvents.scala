package raffle.domain.model

import java.time.OffsetDateTime

// Events ============================================================
sealed trait RaffleEvent {
  def raffleId: RaffleId
}

// Creation Event
case class RaffleCreated(raffleId: RaffleId) extends RaffleEvent
// Update Events
sealed trait RaffleUpdateEvent extends RaffleEvent
case class ParticipantAdded(name: String, raffleId: RaffleId) extends RaffleUpdateEvent
case class ParticipantRemoved(name: String, raffleId: RaffleId) extends RaffleUpdateEvent
case class WinnerSelected(winner: String, date: OffsetDateTime, raffleId: RaffleId) extends RaffleUpdateEvent
