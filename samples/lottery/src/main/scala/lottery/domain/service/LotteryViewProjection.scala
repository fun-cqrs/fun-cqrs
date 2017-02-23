package lottery.domain.service

import io.funcqrs.Projection
import lottery.domain.model.LotteryView.Participant
import lottery.domain.model.{ LotteryView, _ }

import scala.concurrent.Future

class LotteryViewProjection(repo: LotteryViewRepo) extends Projection {

  def receiveEvent: ReceiveEvent = {

    case e: LotteryCreated =>
      Future.successful(repo.save(LotteryView(id = e.lotteryId)))

    case e: LotteryUpdateEvent =>
      Future.successful {
        repo
          .updateById(e.lotteryId) { lot =>
            updateFunc(lot, e)
          }
          .map(_ => ())
      }
  }

  private def updateFunc(view: LotteryView, evt: LotteryUpdateEvent): LotteryView = {
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
    LotteryView.Participant(evt.name)
}
//end::lottery-view-projection[]
