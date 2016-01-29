package io.funcqrs.test.backend

import io.funcqrs.behavior.Behavior
import io.funcqrs._
import io.funcqrs.backend.Backend
import io.funcqrs.config.{ AggregateConfig, ProjectionConfig }
import io.funcqrs.interpreters.{ IdentityInterpreter, Identity }
import scala.collection.immutable
import io.funcqrs.interpreters.Monads._
import scala.reflect.ClassTag

class InMemoryBackend extends Backend[Identity] {

  private var aggregates: Map[AggregateId, AggregateRef[_, Identity]] = Map()

  private val stream: Stream[DomainEvent] = Stream()

  def aggregateRef[A <: AggregateLike](id: A#Id)(implicit tag: ClassTag[A]): AggregateRef[A, Identity] = ???

  def configure[A <: AggregateLike](config: AggregateConfig[A])(implicit tag: ClassTag[A]): Backend[Identity] = ???

  def configure(config: ProjectionConfig): Backend[Identity] = ???

  private def publishEvents(evts: immutable.Seq[DomainEvent]): Unit = {
    evts foreach publishEvent
  }
  private def publishEvent(evt: DomainEvent): Unit = ()

  class InMemoryAggregateRef[A <: AggregateLike](behavior: Behavior[A]) extends IdentityAggregateRef[A] {

    private var aggregateState: Option[A] = None

    val interpreter = IdentityInterpreter(behavior)

    def ask(cmd: Command): Identity[Events] = {
      aggregateState
        .map { agg => update(agg, cmd) }
        .getOrElse { create(cmd) }
    }

    private def update(aggregate: Aggregate, cmd: Command): interpreter.Events = {
      val (events, updatedAgg) = interpreter.applyCommand(cmd, aggregate)
      aggregateState = Option(updatedAgg)
      publishEvents(events)
      events
    }

    private def create(cmd: Command): interpreter.Events = {
      val (events, aggregate) = interpreter.applyCommand(cmd)
      aggregateState = Option(aggregate)
      publishEvents(events)
      events
    }

    def state(): Identity[A] = aggregateState.get

    def exists(): Identity[Boolean] = aggregateState.isDefined
  }
}
