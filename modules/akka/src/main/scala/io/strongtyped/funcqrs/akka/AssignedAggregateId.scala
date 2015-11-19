package io.strongtyped.funcqrs.akka

import io.strongtyped.funcqrs.{AggregateAliases, AggregateLike}

trait AssignedAggregateId {
  this: AggregateManager =>

  override def processCreation: Receive = {
    case (id: Id @unchecked, cmd: Command) => processAggregateCommand(id, cmd)
  }
}
