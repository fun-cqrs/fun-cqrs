package io.funcqrs.test.backend

import io.funcqrs._
import io.funcqrs.backend.{ QuerySelectAll, Backend, QueryByTag, QueryByTags }
import io.funcqrs.behavior.Behavior
import io.funcqrs.config.{ AggregateConfig, ProjectionConfig }
import io.funcqrs.interpreters.Monads._
import io.funcqrs.interpreters.{ Identity, IdentityInterpreter }
import rx.lang.scala.Subject
import rx.lang.scala.subjects.PublishSubject

import scala.collection.concurrent.TrieMap
import scala.collection.{ concurrent, immutable }
import scala.concurrent.Await
import scala.reflect.ClassTag
import scala.concurrent.duration._

class InMemoryBackend extends Backend[Identity] {

  private var aggregateConfigs: concurrent.Map[ClassTag[_], AggregateConfig[_]] = concurrent.TrieMap()
  private var aggregates: concurrent.Map[AggregateId, IdentityAggregateRef[_]] = TrieMap()

  private val eventStream: Subject[DomainEvent] = PublishSubject()

  private val stream: Stream[DomainEvent] = Stream()

  def aggregateRef[A <: AggregateLike: ClassTag](id: A#Id): InMemoryAggregateRef[A] = {

    aggregates.getOrElseUpdate(
      id,
      { // build new aggregateRef if not existent
        val config = aggregateConfigs(ClassTagImplicits[A]).asInstanceOf[AggregateConfig[A]]
        val behavior = config.behavior(id)
        new InMemoryAggregateRef(behavior)
      }
    ).asInstanceOf[InMemoryAggregateRef[A]]
  }

  def configure[A <: AggregateLike: ClassTag](config: AggregateConfig[A]): Backend[Identity] = {
    aggregateConfigs += (ClassTagImplicits[A] -> config)
    this
  }

  def configure(config: ProjectionConfig): Backend[Identity] = {

    // does the event match the query criteria?
    def matchQuery(evt: DomainEvent with MetadataFacet[_]): Boolean = {

      config.query match {
        case QueryByTag(tag) => evt.tags.contains(tag)
        case QueryByTags(tags) => tags.subsetOf(evt.tags)
        case QuerySelectAll => true
      }
    }

    //noinspection MatchToPartialFunction
    eventStream.subscribe { evt: DomainEvent =>

      evt match {
        case evt: DomainEvent with MetadataFacet[_] if matchQuery(evt) =>
          // TODO: projections should be interpreted as well to avoid this
          Await.ready(config.projection.onEvent(evt), 10.seconds)
          ()
        case anyEvent => // do nothing, don't send to projection
      }
    }

    this
  }

  private def publishEvents(evts: immutable.Seq[DomainEvent]): Unit = {
    evts foreach publishEvent
  }

  private def publishEvent(evt: DomainEvent): Unit = {
    eventStream.onNext(evt)
  }

  class InMemoryAggregateRef[A <: AggregateLike](behavior: Behavior[A]) extends IdentityAggregateRef[A] {

    private var aggregateState: Option[A] = None

    val interpreter = IdentityInterpreter(behavior)

    def ask(cmd: Command): Identity[Events] =
      handle(aggregateState, cmd)

    def tell(cmd: Command): Unit = {
      ask(cmd)
      () // omit events
    }

    private def handle(optionalAggregate: Option[Aggregate], cmd: Command): interpreter.Events = {
      val (events, updatedAgg) = interpreter.applyCommand(cmd, optionalAggregate)
      aggregateState = updatedAgg
      publishEvents(events)
      events
    }

    def state(): Identity[A] = aggregateState.get

    def exists(): Identity[Boolean] = aggregateState.isDefined

  }
}
