package io.strongtyped.funcqrs.akka

trait AssignedAggregateId {
  this: AggregateManager =>

  override def processCreation: Receive = {
    case (id: AggregateType#Identifier@unchecked, cmd: AggregateType#Protocol#ProtocolCommand) => processAggregateCommand(id, cmd)
  }
}
