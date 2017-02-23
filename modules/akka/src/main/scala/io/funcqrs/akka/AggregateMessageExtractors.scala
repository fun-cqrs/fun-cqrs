package io.funcqrs.akka

import io.funcqrs.behavior.AggregateAliases

import scala.util.{ Success, Try }

trait AggregateMessageExtractors extends AggregateAliases {

  object IdAndCommand {
    def unapply(cmdMsg: AggregateManager.UntypedIdAndCommand): Option[(Id, Command)] = {
      (cmdMsg.id, cmdMsg.cmd) match {
        case (GoodId(id), TypedCommand(cmd)) => Some(id, cmd)
        case _                               => None
      }
    }
  }

  object GoodId {

    def unapply(aggregateId: Any): Option[Id] = {
      Try(aggregateId.asInstanceOf[Id]) match {
        case Success(casted) => Some(casted)
        case _               => None
      }
    }
  }

  object BadId {

    def unapply(aggregateId: Any): Option[Any] = {
      Try(aggregateId.asInstanceOf[Id]) match {
        case Success(_) => None
        case _          => Some(aggregateId)
      }
    }
  }

  object TypedEvent {
    def unapply(any: Any): Option[Event] = {
      Try(any.asInstanceOf[Event]) match {
        case Success(casted) => Option(casted)
        case _               => None
      }
    }
  }

  object TypedCommand {
    def unapply(any: Any): Option[Command] = {
      Try(any.asInstanceOf[Command]) match {
        case Success(casted) => Option(casted)
        case _               => None
      }
    }
  }
}
