package io.funcqrs.test.backend

import io.funcqrs._
import io.funcqrs.backend.{ Backend, QueryByTag, QueryByTags, QuerySelectAll }
import io.funcqrs.behavior._
import io.funcqrs.behavior.api.Types
import io.funcqrs.config.{ AggregateConfig, ProjectionConfig }
import io.funcqrs.interpreters.{ Identity, IdentityInterpreter }

import scala.collection.concurrent
import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.reflect.ClassTag

class InMemoryBackend extends Backend[Identity] {

  private var aggregateConfigs: concurrent.Map[ClassTag[_], AggregateConfig[_, _, _, _]] = concurrent.TrieMap()
  private var aggregates: concurrent.Map[AggregateId, IdentityAggregateRef[_]]           = TrieMap()

//  private val eventStream: Subject[DomainEvent] = PublishSubject()

//  private val stream: Stream[DomainEvent] = Stream()

  // format: off
  protected def aggregateRefById[A, I <: AggregateId](id: I)
                                                     (implicit types: Types[A], tag: ClassTag[A])
                                                    : InMemoryAggregateRef[A, types.Command, types.Event, types.Id] = {
  // format: on
    type ConfigType = AggregateConfig[A, types.Command, types.Event, I]

    aggregates
      .getOrElseUpdate(
        id, { // build new aggregateRef if not existent

          val config = configLookup[A, types.Command, types.Event, I] {
            aggregateConfigs(ClassTagImplicits[A]).asInstanceOf[ConfigType]
          }

          val behavior = config.behavior(id)
          new InMemoryAggregateRef(id, behavior)
        }
      )
      .asInstanceOf[InMemoryAggregateRef[A, types.Command, types.Event, types.Id]]
  }

  def configure[A: ClassTag, C, E, I](config: AggregateConfig[A, C, E, I]): Backend[Identity] = {
    aggregateConfigs += (ClassTagImplicits[A] -> config)
    this
  }

  def configure(config: ProjectionConfig): Backend[Identity] = {

    // does the event match the query criteria?
    def matchQuery(evt: DomainEvent with MetadataFacet[_]): Boolean = {
      config.query match {
        case QueryByTag(tag)   => evt.tags.contains(tag)
        case QueryByTags(tags) => tags.subsetOf(evt.tags)
        case QuerySelectAll    => true
      }
    }

    def matchQueryWithoutTagging(evt: DomainEvent): Boolean = {
      config.query match {
        case QuerySelectAll => true
        case _              => false
      }
    }

    //noinspection MatchToPartialFunction
//    eventStream.subscribe { evt: DomainEvent =>
//      def sendToProjection(event: DomainEvent) = {
//        // TODO: projections should be interpreted as well to avoid this
//        Await.ready(config.projection.onEvent(evt), 10.seconds)
//        ()
//      }
//      evt match {
//        case evt: DomainEvent with MetadataFacet[_] if matchQuery(evt) =>
//          sendToProjection(evt)
//        case evt: DomainEvent if matchQueryWithoutTagging(evt) =>
//          sendToProjection(evt)
//        case anyEvent => // do nothing, don't send to projection
//      }
//    }

    this
  }

//  private def publishEvents(evts: immutable.Seq[DomainEvent]): Unit = {
//    evts foreach publishEvent
//  }
//
//  private def publishEvent(evt: DomainEvent): Unit = {
//    eventStream.onNext(evt)
//  }

  class InMemoryAggregateRef[A, C, E, I <: AggregateId](id: I, behavior: api.Behavior[A, C, E]) extends IdentityAggregateRef[A] { self =>

    val types = new Types[A] {
      type Id      = I
      type Command = C
      type Event   = E
    }

    private var aggregateState: Option[A] = None

    val interpreter = IdentityInterpreter(behavior)

    def ask(cmd: types.Command): Identity[types.Events] =
      handle(aggregateState, cmd)

    def tell(cmd: types.Command): Unit = {
      ask(cmd)
      () // omit events
    }

    private def handle(state: Option[types.Aggregate], cmd: types.Command): interpreter.Events = {
      val (events, updatedAgg) = interpreter.applyCommand(state, cmd)
      aggregateState = updatedAgg
//      publishEvents(events)
      events
    }

    def state(): Identity[types.Aggregate] =
      aggregateState.getOrElse(sys.error("Aggregate is not initialized"))

    def exists(): Identity[Boolean] = aggregateState.isDefined

    def withAskTimeout(timeout: FiniteDuration): AggregateRef[A, Future] = new AsyncAggregateRef[A] {

      val types = self.types

      def timeoutDuration: FiniteDuration = timeout

      def withAskTimeout(timeout: FiniteDuration): AggregateRef[A, Future] = self.withAskTimeout(timeout)

      def tell(cmd: types.Command): Unit = self.tell(cmd)

      def ask(cmd: types.Command): Future[types.Events] = Future.successful(self.ask(cmd))

      def state(): Future[types.Aggregate] = Future.successful(self.state())

      def exists(): Future[Boolean] = Future.successful(self.exists())
    }
  }
}
