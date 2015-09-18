package fun.cqrs.akka

import fun.cqrs.Aggregate

trait AssignedAggregateId {
  this: AggregateManager =>

  override def processCreation: Receive = {
    case (id: AggregateType#Identifier@unchecked, cmd: AggregateType#Protocol#CreateCmd) => processAggregateCommand(id, cmd)
  }
}
