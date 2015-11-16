package io.strongtyped.funcqrs

import scala.concurrent.{ExecutionContext, Future}
import scala.collection.immutable

trait WriteModelTestSupport {


  implicit class BehaviorOps[A <: AggregateDef](behavior: Behavior[A]) extends AggregateAliases {

    type Aggregate = A

    def applyCommands(cmd: Command, cmds: Command*)(implicit ec: ExecutionContext): Future[(Seq[Event], Aggregate)] = {
      behavior.applyCommand(cmd).flatMap {
        case (evt, agg) =>
          applyCommands(immutable.Seq(evt), agg, cmds: _*)
      }
    }

    private def applyCommands(events: Events, aggregate: Aggregate, cmds: Command*)(implicit ec: ExecutionContext): Future[(Events, Aggregate)] = {
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
            applyCommands(allEvents, agg, tail: _*)
        }
        case Nil => Future.failed(new RuntimeException("Not commands found!!"))
      }
    }
  }
}
