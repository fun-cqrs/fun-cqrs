package io.funcqrs

import scala.util.{ Failure, Success, Try }

trait AggregateMessageExtractors {

  type Aggregate <: AggregateLike

  object IdAndCommand {
    def unapply(cmdMsg: CommandMsg): Option[(Aggregate#Id, Aggregate#Command)] = {

      val extracted =
        for {
          id <- Try(cmdMsg.id.asInstanceOf[Aggregate#Id])
          cmd <- Try(cmdMsg.cmd.asInstanceOf[Aggregate#Command])
        } yield (id, cmd)

      extracted match {
        case Success(value) => Some(value)
        case Failure(exp) =>
          println(s"""
                   | !!! DEBUG PRINTLN - remove me
                   | $exp
                   | ============================================================================
                   """.stripMargin)
          None
      }
    }
  }

  object GoodId {

    def unapply(aggregateId: AggregateId): Option[Aggregate#Id] = {
      // FIXME: this is can't type check properly, need to find a solution
      Try(aggregateId.asInstanceOf[Aggregate#Id]) match {
        case Success(id) => Some(id)
        case _ => None
      }
    }
  }

  object BadId {

    def unapply(aggregateId: AggregateId): Option[AggregateId] = {
      // FIXME: this is can't type check properly, need to find a solution
      Try(aggregateId.asInstanceOf[Aggregate#Id]) match {
        case Success(_) => None
        case _ => Some(aggregateId)
      }
    }
  }

}
