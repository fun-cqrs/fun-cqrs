package raffle.domain.service

import com.typesafe.scalalogging.LazyLogging
import io.strongtyped.funcqrs.{HandleEvent, Projection}
import raffle.domain.model.RaffleProtocol._
import raffle.domain.model.RaffleView

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class RaffleViewProjection(repo: RaffleViewRepo) extends Projection with LazyLogging {


  def receiveEvent: HandleEvent = {
    case e: RaffleCreated     => create(e)
    case e: RaffleUpdateEvent => update(e)
  }

  def create(e: RaffleCreated): Future[Unit] = {
    repo.save(RaffleView(name = e.name, id = e.metadata.aggregateId))
  }

  def update(e: RaffleUpdateEvent): Future[Unit] = {
    repo.updateById(e.aggregateId) { prod =>
      updateFunc(prod, e)
    }.map(_ => ())

  }

  private def updateFunc(view: RaffleView, evt: RaffleUpdateEvent): RaffleView = {
    evt match {
      case e: ParticipantAdded => view.copy(participants = view.participants :+ e.name)
      case e: WinnerSelected   => view.copy(winner = Some(e.winner))
    }
  }
}
