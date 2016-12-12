package io.funcqrs.behavior.api

import scala.collection.immutable

trait Types[A] {

  type Aggregate = A

  type Id
  type Command
  type Event
  type Events = immutable.Seq[Event]

  val actions = Actions[A, Command, Event]()

  implicit def self: this.type = this
}
