package io.strongtyped.funcqrs.akka

import io.strongtyped.funcqrs.{AggregateTypes, AggregateDef}

trait AssignedAggregateId extends AggregateTypes {
  this: AggregateManager =>

  override def processCreation: Receive = {
    case (id: Id @unchecked, cmd: Command) => processAggregateCommand(id, cmd)
  }
}
