package io.strongtyped.funcqrs.akka

import io.strongtyped.funcqrs.{AggregateTypes, AggregateDef}

trait AssignedAggregateId[Aggregate <: AggregateDef] extends AggregateTypes[Aggregate] {
  this: AggregateManager[Aggregate] =>

  override def processCreation: Receive = {
    case (id: Id @unchecked, cmd: Command) => processAggregateCommand(id, cmd)
  }
}
