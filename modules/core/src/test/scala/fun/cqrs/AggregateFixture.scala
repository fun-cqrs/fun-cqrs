package fun.cqrs

import scala.concurrent.{Future, ExecutionContext}


abstract class AggregateFixture[A <: Aggregate](eventBus: EventBus) {

  val behavior: Behavior[A]

  type Protocol = A#Protocol

  def projection: Projection

  eventBus.addHandler {
    case evt if projection.receiveEvent.isDefinedAt(evt) =>
      val pf = projection.receiveEvent
      pf(evt)
  }

  def apply(cmd: Protocol#CreateCmd)(implicit ec: ExecutionContext): Future[A] = {
    val result = behavior.applyCommand(cmd)

    result.map { case (evt, agg) =>
      eventBus.publishEvent(evt)
      agg
    }
  }

  def apply(model: A, cmd: Protocol#UpdateCmd)(implicit ec: ExecutionContext): Future[A] = {
    val result = behavior.applyCommand(model, cmd)
    result.map { case (evts, agg) =>
      eventBus.publishEvents(evts)
      agg
    }
  }
}
