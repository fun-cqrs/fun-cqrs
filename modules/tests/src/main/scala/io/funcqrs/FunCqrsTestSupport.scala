package io.funcqrs

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}

trait FunCqrsTestSupport {

  def sendToProjection(projection: Projection, events: Seq[DomainEvent])(implicit ec: ExecutionContext): Future[Unit] = {
    events.foldLeft(Future.successful(())) { (fut, evt) =>
      fut.flatMap { _ => projection.onEvent(evt) }
    }
  }

  def sendCommands[A <: AggregateLike](behavior: Behavior[A])(cmd: behavior.Command, cmds: behavior.Command*)
                                              (implicit ec: ExecutionContext): Future[(behavior.Events, behavior.Aggregate)] = {

    def applyCommands(events: behavior.Events, aggregate: behavior.Aggregate, cmds: behavior.Command*): Future[(behavior.Events, behavior.Aggregate)] = {
      cmds.toList match {
        case head :: Nil  => behavior.applyCommand(head, aggregate).map {
          case (evts, agg) =>
            // concat previous events with events from last command
            val allEvents = events ++ evts
            (allEvents, agg)
        }
        case head :: tail => behavior.applyCommand(head, aggregate).flatMap {
          case (evts, agg) =>
            // concat previous events with events from last command
            val allEvents = events ++ evts
            applyCommands(allEvents, agg, tail: _*)
        }
        case Nil          => Future.failed(new RuntimeException("Not commands found!!"))
      }
    }

    behavior.applyCommand(cmd).flatMap {
      case (evt, agg) =>
        applyCommands(immutable.Seq(evt), agg, cmds: _*)
    }
  }

  class End2EndTestSupport(projection: Projection) {

    implicit class BehaviorOps[A <: AggregateLike](behavior: Behavior[A]) {

      def applyCommands(cmd: behavior.Command, cmds: behavior.Command*)(implicit ec: ExecutionContext): Future[Unit] = {
        val resultWriteModel = sendCommands(behavior)(cmd, cmds: _*)
        resultWriteModel.flatMap {
          case (evts, _) => sendToProjection(projection, evts)
        }
      }
    }

  }


  trait ReadModelTestSupport {

    implicit class ProjectionOps(projection: Projection)(implicit ec: ExecutionContext) {

      def applyEvents(events: Seq[DomainEvent]): Future[Unit] = {
        sendToProjection(projection, events)
      }
    }

  }


  trait WriteModelTestSupport {

    implicit class BehaviorOps[A <: AggregateLike](val behavior: Behavior[A]) {
      def applyCommands(cmd: behavior.Command, cmds: behavior.Command*)(implicit ec: ExecutionContext): Future[(behavior.Events, behavior.Aggregate)] = {
        sendCommands(behavior)(cmd, cmds: _*)
      }
    }
  }
}






