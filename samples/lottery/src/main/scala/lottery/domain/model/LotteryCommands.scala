package lottery.domain.model


/** Defines the Lottery Protocol, all Commands it may receive and Events it may emit */
// Commands ============================================================
sealed trait LotteryCommand
// Creation Command
case object CreateLottery extends LotteryCommand

// Update Commands
case class AddParticipant(name: String) extends LotteryCommand

case class RemoveParticipant(name: String) extends LotteryCommand

case object RemoveAllParticipants extends LotteryCommand

case object Run extends LotteryCommand