package lottery.domain.service

import com.typesafe.scalalogging.LazyLogging
import io.funcqrs.Projection
import io.funcqrs.HandleEvent
import lottery.domain.model.LotteryProtocol._
import lottery.domain.model.LotteryView

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class LotteryViewProjection(repo: LotteryViewRepo) extends Projection with LazyLogging {

  def handleEvent: HandleEvent = {
    case e: LotteryCreated     => create(e)
    case e: LotteryUpdateEvent => update(e)

  }

  def create(e: LotteryCreated): Future[Unit] = {
    repo.save(LotteryView(name = e.name, id = e.metadata.aggregateId))
  }

  def update(e: LotteryUpdateEvent): Future[Unit] = {
    repo.updateById(e.aggregateId) { lot =>
      updateFunc(lot, e)
    }.map(_ => ())

  }

  private def updateFunc(view: LotteryView, evt: LotteryUpdateEvent): LotteryView = {
    evt match {
      case e: ParticipantAdded   => view.copy(participants = view.participants :+ newParticipant(e))
      case e: WinnerSelected     => view.copy(winner = Some(e.winner), runDate = Some(e.date))
      case e: ParticipantRemoved => view.copy(participants = view.participants.filter(_.name != e.name))
    }
  }

  private def newParticipant(evt: ParticipantAdded): LotteryView.Participant =
    LotteryView.Participant(evt.name, evt.date)
}
