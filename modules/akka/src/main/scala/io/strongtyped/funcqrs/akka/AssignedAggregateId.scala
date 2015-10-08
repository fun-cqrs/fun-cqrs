package io.strongtyped.funcqrs.akka

trait AssignedAggregateId {
  this: AggregateManager =>

  override def processCreation: Receive = {
    case (id: AggregateType#Id@unchecked, cmd: AggregateType#Protocol#ProtocolCommand) => processAggregateCommand(id, cmd)
  }
}
