package fun.cqrs.akka

import akka.actor.Actor
import akka.stream.scaladsl.Source
import fun.cqrs.{DomainEvent, Tag}

trait ProjectionSource {
  this: Actor =>

  def source: Source[DomainEvent, Unit]
}
