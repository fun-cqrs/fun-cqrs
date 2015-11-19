package io.strongtyped.funcqrs

import scala.concurrent.{ Future, ExecutionContext }

abstract class AggregateFixture[A <: AggregateLike](eventBus: EventBus) extends AggregateAliases {

  type Aggregate = A

  val behavior: Behavior[Aggregate]
  
  def projection: Projection

  eventBus.addHandler {
    case evt if projection.receiveEvent.isDefinedAt(evt) =>
      val pf = projection.receiveEvent
      pf(evt)
  }

  def apply(cmd: Command)(implicit ec: ExecutionContext): Future[Aggregate] = {
    val result = behavior.applyCommand(cmd)

    result.map {
      case (evt, agg) =>
        eventBus.publishEvent(evt)
        agg
    }
  }

  def apply(model: Aggregate, cmd: Command)(implicit ec: ExecutionContext): Future[Aggregate] = {
    val result = behavior.applyCommand(cmd, model)
    result.map {
      case (evts, agg) =>
        eventBus.publishEvents(evts)
        agg
    }
  }
}
