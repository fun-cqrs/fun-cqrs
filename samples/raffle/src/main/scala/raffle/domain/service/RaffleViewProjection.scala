package raffle.domain.service

import io.funcqrs.projections.Projection
import raffle.domain.model.RaffleView.Participant
import raffle.domain.model.{ RaffleView, _ }

import scala.concurrent.Future

class RaffleViewProjection(repo: RaffleViewRepo) extends Projection[RaffleEvent] {

  def handleEvent = attempt.HandleEvent {

    case e: RaffleCreated =>
      repo.save(RaffleView(id = e.raffleId))

    case e: RaffleUpdateEvent =>
      repo
        .updateById(e.raffleId) { lot =>
          updateFunc(lot, e)
        }
        .map(_ => ())
  }

  override def onFailure: OnFailure = {
    case (e, _) => Future.successful(())
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
