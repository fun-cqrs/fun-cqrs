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

  // @todo (lucd) not sure about deleting this one

  class End2EndTestSupport(projection: Projection, atMost: Duration = 3.seconds) extends WriteModelOps with ReadModelOps {

    implicit class BehaviorOps[A <: AggregateLike](val behavior: Behavior[A]) {

      def newInstance(cmd: behavior.Command): Identity[RichTestAggregate[behavior.Aggregate]] = {
        val (evts, aggregate) = sendCommandsInternal(behavior)(immutable.Seq(), None, cmd)

        // TODO: we block for the moment, can be removed if projection returns F[_] instead of Future
        val projectionResult = sendToProjectionInternal(projection, evts)
        Await.ready(projectionResult, atMost)

        RichTestAggregate(aggregate, behavior)
      }

    }

    case class RichTestAggregate[A <: AggregateLike](optionalAggregate: Option[A], behavior: Behavior[A]) extends WriteModelOps with ReadModelOps {

      def id: A#Id = optionalAggregate.get.id

      def update(cmds: behavior.Command*): Identity[RichTestAggregate[A]] = {

        val (evts, updatedAgg) = sendCommandsInternal(behavior)(immutable.Seq(), optionalAggregate, cmds: _*)

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

      def newInstance(cmd: behavior.Command): Identity[(behavior.Events, Option[behavior.Aggregate])] = {
        sendCommandsInternal(behavior)(immutable.Seq(), None, cmd)
      }

      def sendCommands(events: behavior.Events, cmd: behavior.Command, cmds: behavior.Command*): Identity[(behavior.Events, Option[behavior.Aggregate])] = {
        sendCommandsInternal(behavior)(events, None, cmd :: cmds.toList: _*)
      }

      def sendUpdateCommands(events: behavior.Events, optionalAggregate: Option[behavior.Aggregate], cmds: behavior.Command*): Identity[(behavior.Events, Option[behavior.Aggregate])] = {
        sendCommandsInternal(behavior)(events, optionalAggregate, cmds: _*)
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

    protected def sendCommandsInternal[A <: AggregateLike](behavior: Behavior[A])(
      events: behavior.Events,
      optionalAggregate: Option[behavior.Aggregate],
      cmds: behavior.Command*
    ): Identity[(behavior.Events, Option[behavior.Aggregate])] = {

      val interpreter = IdentityInterpreter(behavior)

      cmds.toList match {
        case head :: Nil =>
          monad(interpreter.applyCommand(head, optionalAggregate)).map {
            case (evts, aggOpt) =>
              // concat previous events with events from last command
              (events ++ evts, aggOpt)
          }
        case head :: tail =>
          monad(interpreter.applyCommand(head, optionalAggregate)).flatMap {
            case (evts, aggOpt) =>
              sendCommandsInternal(behavior)(events ++ evts, aggOpt, tail: _*)
          }
        case Nil => (events, optionalAggregate)
      }
    }

  }

}

