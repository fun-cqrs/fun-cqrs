package io.funcqrs.akka

import io.funcqrs.AggregateId
import io.funcqrs.behavior.AggregateAliases

import scala.util.{ Failure, Success, Try }

trait AggregateMessageExtractors extends AggregateAliases {

  object IdAndCommand {
    def unapply(cmdMsg: AggregateManager.UntypedIdAndCommand[Id, Command]): Option[(Id, Command)] = {

      val extracted =
        for {
          id <- Try(cmdMsg.id)
          cmd <- Try(cmdMsg.cmd)
        } yield (id, cmd)

      extracted match {
        case Success(value) => Some(value)
        case Failure(exp)   => None
      }
    }
  }

  object GoodId {

    def unapply(aggregateId: Id): Option[Id] = {
      Try(aggregateId) match {
        case Success(id) => Some(id)
        case _           => None
      }
    }
  }

  object BadId {

    def unapply(aggregateId: Id): Option[AggregateId] = {
      Try(aggregateId) match {
        case Success(id) => None
        case _           => Some(aggregateId)
      }
    }
  }

}
