package io.funcqrs.behavior

import io.funcqrs.AggregateId

import scala.collection.immutable

trait Types[A] extends AggregateAliases {

  type Aggregate = A

  type Id <: AggregateId
  type Command
  type Event

  val actions = Actions[A, Command, Event]()

  implicit def self: this.type = this

}

trait AggregateAliases {
  type Aggregate

  type Id <: AggregateId
  type Command
  type Event
  type Events = immutable.Seq[Event]
}
