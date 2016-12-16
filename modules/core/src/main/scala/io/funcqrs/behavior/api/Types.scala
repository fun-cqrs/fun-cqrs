package io.funcqrs.behavior.api

import io.funcqrs.AggregateId

import scala.collection.immutable

trait Types[A] {

  type Aggregate = A

  type Id <: AggregateId
  type Command
  type Event
  type Events = immutable.Seq[Event]

  val actions = Actions[A, Command, Event]()

  implicit def self: this.type = this
}

object Types {
  type Aux[A0, I0] = Types[A0] { type Id = I0 }

  implicit def types2Aux[A](implicit types: Types[A]): Aux[A, types.Id] = types
}
