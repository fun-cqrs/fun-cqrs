package io.funcqrs

import scala.util.{ Success, Try }

trait AggregateIdExtractors {

  type Aggregate <: AggregateLike

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
