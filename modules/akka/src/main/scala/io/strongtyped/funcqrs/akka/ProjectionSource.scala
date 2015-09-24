package io.strongtyped.funcqrs.akka

import akka.actor.Actor
import akka.stream.scaladsl.Source
import io.strongtyped.funcqrs.{DomainEvent, Tag}

trait ProjectionSource {
  this: Actor =>

  def source: Source[DomainEvent, Unit]
}
