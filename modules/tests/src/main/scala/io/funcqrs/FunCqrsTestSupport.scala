package io.funcqrs

import scala.collection.immutable
import scala.concurrent.{ ExecutionContext, Future }

trait FunCqrsTestSupport {

  class End2EndTestSupport(projection: Projection) extends WriteModelOps with ReadModelOps {

    implicit class BehaviorOps[A <: AggregateLike](val behavior: Behavior[A]) {

      def newInstance(cmd: behavior.Command)(implicit ec: ExecutionContext): Future[RichTestAggregate[behavior.Aggregate]] = {
        for {
          (evt, aggregate) <- sendCreateCommandInternal(behavior)(cmd)
          _ <- sendToProjectionInternal(projection, evt)
        } yield RichTestAggregate(aggregate, behavior)
      }
    }

    implicit class FutureRichAggregateOps[A <: AggregateLike](val fut: Future[RichTestAggregate[A]]) {
      def update(cmds: A#Command*)(implicit ec: ExecutionContext): Future[RichTestAggregate[A]] = {
        fut.flatMap { richAggregate =>
          richAggregate.update(cmds: _*)
        }
      }
    }

    case class RichTestAggregate[A <: AggregateLike](aggregate: A, behavior: Behavior[A]) extends WriteModelOps with ReadModelOps {

      def id: A#Id = aggregate.id

      def update(cmds: behavior.Command*)(implicit ec: ExecutionContext): Future[RichTestAggregate[A]] = {
        for {
          (evts, aggregate) <- sendUpdateCommandsInternal(behavior)(immutable.Seq(), aggregate, cmds: _*)
          _ <- sendToProjectionInternal(projection, evts)
        } yield RichTestAggregate(aggregate, behavior)
      }
    }

  }

  trait ReadModelTestSupport extends ReadModelOps {

    implicit class ProjectionOps(projection: Projection)(implicit ec: ExecutionContext) {

      def sendToProjection(event: DomainEvent): Future[Unit] = {
        sendToProjectionInternal(projection, event)
      }

      def sendToProjection(events: Seq[DomainEvent]): Future[Unit] = {
        sendToProjectionInternal(projection, events)
      }
    }

  }

  trait WriteModelTestSupport extends WriteModelOps {

    implicit class BehaviorOps[A <: AggregateLike](val behavior: Behavior[A]) {

      def sendCreateCommand(cmd: behavior.Command)(implicit ec: ExecutionContext): Future[(behavior.Event, behavior.Aggregate)] = {
        sendCreateCommandInternal(behavior)(cmd)
      }

      def sendCommands(cmd: behavior.Command, cmds: behavior.Command*)(implicit ec: ExecutionContext): Future[(behavior.Events, behavior.Aggregate)] = {
        sendCommandsInternal(behavior)(cmd, cmds: _*)
      }

      def sendUpdateCommands(aggregate: behavior.Aggregate, cmds: behavior.Command*)(implicit ec: ExecutionContext): Future[(behavior.Events, behavior.Aggregate)] = {
        sendUpdateCommandsInternal(behavior)(immutable.Seq(), aggregate, cmds: _*)
      }
    }
  }

  trait ReadModelOps {

    protected def sendToProjectionInternal(projection: Projection, event: DomainEvent)(implicit ec: ExecutionContext): Future[Unit] = {
      projection.onEvent(event)
    }

    protected def sendToProjectionInternal(projection: Projection, events: Seq[DomainEvent])(implicit ec: ExecutionContext): Future[Unit] = {
      events.foldLeft(Future.successful(())) { (fut, evt) =>
        fut.flatMap { _ => projection.onEvent(evt) }
      }
    }
  }

  trait WriteModelOps {

    protected def sendCreateCommandInternal[A <: AggregateLike](behavior: Behavior[A])(cmd: behavior.Command, cmds: behavior.Command*)(implicit ec: ExecutionContext): Future[(behavior.Event, behavior.Aggregate)] = {
      behavior.applyCommand(cmd)
    }
    protected def sendCommandsInternal[A <: AggregateLike](behavior: Behavior[A])(cmd: behavior.Command, cmds: behavior.Command*)(implicit ec: ExecutionContext): Future[(behavior.Events, behavior.Aggregate)] = {

      sendCreateCommandInternal(behavior)(cmd).flatMap {
        case (evt, agg) => sendUpdateCommandsInternal(behavior)(immutable.Seq(evt), agg, cmds: _*)
      }
    }

    protected def sendUpdateCommandsInternal[A <: AggregateLike](behavior: Behavior[A])(events: behavior.Events, aggregate: behavior.Aggregate, cmds: behavior.Command*)(implicit ec: ExecutionContext): Future[(behavior.Events, behavior.Aggregate)] = {

      cmds.toList match {
        case head :: Nil => behavior.applyCommand(head, aggregate).map {
          case (evts, agg) =>
            // concat previous events with events from last command
            val allEvents = events ++ evts
            (allEvents, agg)
        }
        case head :: tail => behavior.applyCommand(head, aggregate).flatMap {
          case (evts, agg) =>
            // concat previous events with events from last command
            val allEvents = events ++ evts
            sendUpdateCommandsInternal(behavior)(allEvents, agg, tail: _*)
        }
        case Nil => Future.successful((events, aggregate))
      }
    }
  }

}

