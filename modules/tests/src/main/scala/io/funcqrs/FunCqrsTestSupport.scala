package io.funcqrs

import io.funcqrs.behavior.Behavior
import io.funcqrs.interpreters.{ Monads, IdentityInterpreter, Identity }
import io.funcqrs.test.backend.InMemoryBackend

import scala.collection.immutable
import scala.concurrent.duration._
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.concurrent.ExecutionContext.Implicits.global
import io.funcqrs.interpreters.Monads._

import scala.reflect.ClassTag

trait FunCqrsTestSupport {

  trait InMemoryTestSupport {

    private lazy val backend = {
      val backend = new InMemoryBackend
      configure(backend)
      backend
    }

    def configure(backend: InMemoryBackend): Unit

    def aggregateRef[A <: AggregateLike](id: A#Id)(implicit tag: ClassTag[A]): IdentityAggregateRef[A] = {
      backend.aggregateRef[A](id)
    }
  }

  class End2EndTestSupport(projection: Projection, atMost: Duration = 3.seconds) extends WriteModelOps with ReadModelOps {

    implicit class BehaviorOps[A <: AggregateLike](val behavior: Behavior[A]) {

      def newInstance(cmd: behavior.Command): Identity[RichTestAggregate[behavior.Aggregate]] = {
        val (evts, aggregate) = sendCreateCommandInternal(behavior)(cmd)

        // TODO: we block for the moment, can be removed if projection returns F[_] instead of Future
        val projectionResult = sendToProjectionInternal(projection, evts)
        Await.ready(projectionResult, atMost)

        RichTestAggregate(aggregate, behavior)
      }

    }

    case class RichTestAggregate[A <: AggregateLike](aggregate: A, behavior: Behavior[A]) extends WriteModelOps with ReadModelOps {

      def id: A#Id = aggregate.id

      def update(cmds: behavior.Command*): Identity[RichTestAggregate[A]] = {

        val (evts, updatedAgg) = sendUpdateCommandsInternal(behavior)(immutable.Seq(), aggregate, cmds: _*)

        // TODO: we block for the moment, can be removed if projection returns F[_] instead of Future
        val projectionResult = sendToProjectionInternal(projection, evts)
        Await.ready(projectionResult, atMost)

        RichTestAggregate(updatedAgg, behavior)

      }
    }

  }

  trait ReadModelTestSupport extends ReadModelOps {

    implicit class ProjectionOps(projection: Projection) {

      def sendToProjection(event: DomainEvent): Future[Unit] = {
        sendToProjectionInternal(projection, immutable.Seq(event))
      }

      def sendToProjection(events: Seq[DomainEvent]): Future[Unit] = {
        sendToProjectionInternal(projection, events)
      }
    }

  }

  trait WriteModelTestSupport extends WriteModelOps {

    implicit class BehaviorOps[A <: AggregateLike](val behavior: Behavior[A]) {

      def newInstance(cmd: behavior.Command): Identity[(behavior.Events, behavior.Aggregate)] = {
        sendCreateCommandInternal(behavior)(cmd)
      }

      def sendCommands(cmd: behavior.Command, cmds: behavior.Command*): Identity[(behavior.Events, behavior.Aggregate)] = {
        sendCommandsInternal(behavior)(cmd, cmds: _*)
      }

      def sendUpdateCommands(aggregate: behavior.Aggregate, cmds: behavior.Command*): Identity[(behavior.Events, behavior.Aggregate)] = {
        sendUpdateCommandsInternal(behavior)(immutable.Seq(), aggregate, cmds: _*)
      }
    }
  }

  trait ReadModelOps {

    protected def sendToProjectionInternal(projection: Projection, events: Seq[DomainEvent]): Future[Unit] = {
      events.foldLeft(Future.successful(())) { (fut, evt) =>
        fut.flatMap { _ => projection.onEvent(evt) }
      }
    }
  }

  trait WriteModelOps {

    protected def sendCreateCommandInternal[A <: AggregateLike](behavior: Behavior[A])(cmd: behavior.Command): Identity[(behavior.Events, behavior.Aggregate)] = {
      val evts = IdentityInterpreter(behavior).handleCommand(cmd)

      val agg = behavior.onEvent(evts.head)

      val updatedAgg =
        evts.tail.foldLeft(agg) {
          case (currentAgg, evt) =>
            behavior.onEvent(currentAgg, evt)
        }

      (evts, updatedAgg)
    }
    protected def sendCommandsInternal[A <: AggregateLike](behavior: Behavior[A])(cmd: behavior.Command, cmds: behavior.Command*): Identity[(behavior.Events, behavior.Aggregate)] = {

      val (evtsCreation, agg) = sendCreateCommandInternal(behavior)(cmd)

      sendUpdateCommandsInternal(behavior)(evtsCreation, agg, cmds: _*)

    }

    protected def sendUpdateCommandsInternal[A <: AggregateLike](behavior: Behavior[A])(
      events: behavior.Events,
      aggregate: behavior.Aggregate,
      cmds: behavior.Command*
    ): Identity[(behavior.Events, behavior.Aggregate)] = {

      val interpreter = IdentityInterpreter(behavior)

      cmds.toList match {
        case head :: Nil =>
          monad(interpreter.applyCommand(head, aggregate)).map {
            case (evts, agg) =>
              // concat previous events with events from last command
              val allEvents = events ++ evts
              (allEvents, agg)
          }
        case head :: tail =>
          monad(interpreter.applyCommand(head, aggregate)).flatMap {
            case (evts, agg) =>
              // concat previous events with events from last command
              val allEvents = events ++ evts
              sendUpdateCommandsInternal(behavior)(allEvents, agg, tail: _*)
          }
        case Nil => (events, aggregate)
      }
    }
  }

}

