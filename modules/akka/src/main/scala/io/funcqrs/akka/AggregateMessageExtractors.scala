package io.funcqrs.akka

import io.funcqrs.{ AggregateAliases, AggregateId, AggregateLike }

import scala.util.{ Failure, Success, Try }

trait AggregateMessageExtractors extends AggregateAliases {

  object IdAndCommand {
    def unapply(cmdMsg: AggregateManager.UntypedIdAndCommand): Option[(Id, Command)] = {

      val extracted =
        for {
          id <- Try(cmdMsg.id.asInstanceOf[Id])
          cmd <- Try(cmdMsg.cmd.asInstanceOf[Command])
        } yield (id, cmd)

      extracted match {
        case Success(value) => Some(value)
        case Failure(exp) => None
      }
    }
  }

  object GoodId {

    def unapply(aggregateId: Aggregate#Id): Option[Aggregate#Id] = {
      Try(aggregateId) match {
        case Success(id) => Some(id)
        case _ => None
      }
    }
  }

  object BadId {

    def unapply(aggregateId: Aggregate#Id): Option[AggregateId] = {
      Try(aggregateId) match {
        case Success(id) => None
        case _ => Some(aggregateId)
      }
    }
  }

}
