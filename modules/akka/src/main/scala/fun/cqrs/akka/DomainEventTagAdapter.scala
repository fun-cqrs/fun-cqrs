package fun.cqrs.akka

import akka.persistence.journal.{Tagged, WriteEventAdapter}
import fun.cqrs.DomainEvent

class DomainEventTagAdapter extends WriteEventAdapter {

  def manifest(event: Any): String = ""

  def toJournal(event: Any): Any = {
    event match {

      case evt: DomainEvent =>
        if (evt.metadata.tags.nonEmpty) tag(evt)
        else evt

      case _ => event
    }
  }

  private def tag(evt: DomainEvent): Tagged = {
    val tags = evt.metadata.tags.map(_.value)
    Tagged(evt, tags)
  }
}
