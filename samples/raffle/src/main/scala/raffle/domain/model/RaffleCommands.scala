package raffle.domain.model

/* all Commands it may receive and Events it may emit */
sealed trait RaffleCommand

case object CreateRaffle extends RaffleCommand

case class AddParticipant(name: String) extends RaffleCommand

case class RemoveParticipant(name: String) extends RaffleCommand

case object RemoveAllParticipants extends RaffleCommand

case object Run extends RaffleCommand
