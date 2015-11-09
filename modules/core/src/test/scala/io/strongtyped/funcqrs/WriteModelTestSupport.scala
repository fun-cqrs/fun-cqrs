package io.strongtyped.funcqrs

import scala.concurrent.{ExecutionContext, Future}

trait WriteModelTestSupport {


  implicit class BehaviorOps[A <: Aggregate](behavior: Behavior[A]) {

    type Command = A#Protocol#ProtocolCommand
    type Event = A#Protocol#ProtocolEvent

    def applyCommands(cmd: Command, cmds: Command*)(implicit ec: ExecutionContext): Future[(Seq[Event], A)] = {
      behavior.applyCommand(cmd).flatMap {
        case (evt, agg) =>
          applyCommands(Seq(evt), agg, cmds: _*)
      }
    }

    private def applyCommands(events: Seq[Event], aggregate: A, cmds: Command*)(implicit ec: ExecutionContext): Future[(Seq[Event], A)] = {
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
