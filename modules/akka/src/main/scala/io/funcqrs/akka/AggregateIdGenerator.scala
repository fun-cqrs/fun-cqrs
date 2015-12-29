package io.funcqrs.akka

import io.funcqrs._

trait AssignedAggregateId {
  self: AggregateManager =>

  val idStrategy = new AssignedIdStrategy[Aggregate]
}

trait GeneratedAggregateId {
  autoGen: AggregateManager =>

  def generateId(): Id

  val idStrategy = new GeneratedIdStrategy[Aggregate] {
    def generateId(): Id = autoGen.generateId()
  }
}

/**
 * Provides means to manage a Singleton Aggregate.
 * A Singleton Aggregate has a fixed Id and therefore there must exist only one instance in the whole system.
 */
trait SingletonAggregateId {
  singleton: AggregateManager =>

  val id: Id

  val idStrategy = new SingletonIdStrategy[Aggregate] {
    val id: Id = singleton.id
  }
}

