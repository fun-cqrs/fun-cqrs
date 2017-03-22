package raffle.domain.service

import io.funcqrs.Projection
import raffle.domain.model.RaffleView.Participant
import raffle.domain.model.{ RaffleView$, _ }

import scala.concurrent.Future

class RaffleViewProjection(repo: RaffleViewRepo) extends Projection {

  def handleEvent: HandleEvent = {

    case e: RaffleCreated =>
      Future.successful(repo.save(RaffleView(id = e.lotteryId)))

    case e: RaffleUpdateEvent =>
      Future.successful {
        repo
          .updateById(e.lotteryId) { lot =>
            updateFunc(lot, e)
          }
          .map(_ => ())
      }
  }

  private def updateFunc(view: RaffleView, evt: RaffleUpdateEvent): RaffleView = {
    evt match {

      case e: ParticipantAdded =>
        view.copy(participants = view.participants :+ newParticipant(e))

      case e: WinnerSelected =>
        view.copy(winner = Some(e.winner), runDate = Some(e.date))

      case e: ParticipantRemoved =>
        view.copy(participants = view.participants.filter(_.name != e.name))
    }
  }

  private def newParticipant(evt: ParticipantAdded): Participant =
    RaffleView.Participant(evt.name)
}
//end::lottery-view-projection[]
