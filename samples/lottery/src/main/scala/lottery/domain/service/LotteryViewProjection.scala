package lottery.domain.service

//tag::lottery-view-projection[]
import io.funcqrs.Projection
import io.funcqrs.HandleEvent
import lottery.domain.model.LotteryProtocol._
import lottery.domain.model.LotteryView
import lottery.domain.model.LotteryView.Participant

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class LotteryViewProjection(repo: LotteryViewRepo) extends Projection {

  def handleEvent: HandleEvent = {
    
    case e: LotteryCreated => 
      repo.save(LotteryView(name = e.name, id = e.metadata.aggregateId))
    
    case e: LotteryUpdateEvent => update(e)
      repo.updateById(e.metadata.aggregateId) { lot =>
        updateFunc(lot, e)
      }.map(_ => ())
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
    LotteryView.Participant(evt.name, evt.date)
}
//end::lottery-view-projection[]