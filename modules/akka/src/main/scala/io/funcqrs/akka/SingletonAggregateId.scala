package io.funcqrs.akka

import io.funcqrs.Behavior

/** Provides means to manage a Singleton Aggregate. */
trait SingletonAggregateId {
  this: AggregateManager =>

  val id: Id

  override def processCreation: Receive = {
    case cmd: Command => processAggregateCommand(id, cmd)
  }

  def behavior(id: Id): Behavior[Aggregate] = behavior()

  def behavior(): Behavior[Aggregate]

}