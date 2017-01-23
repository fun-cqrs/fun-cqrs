package lottery.domain.service

//tag::lottery-view-projection[]
import io.funcqrs.projections._
import io.funcqrs.HandleEvent
import lottery.domain.model._
import lottery.domain.model.LotteryView
import lottery.domain.model.LotteryView.Participant

import scala.concurrent.Future

class LotteryViewProjection(repo: LotteryViewRepo) extends Projection {

  def receiveEvent: ReceiveEvent = {

    case Envelope(e: LotteryCreated, _) =>
      Future.successful(repo.save(LotteryView(id = e.lotteryId)))

    case Envelope(e: LotteryUpdateEvent, _) =>
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
