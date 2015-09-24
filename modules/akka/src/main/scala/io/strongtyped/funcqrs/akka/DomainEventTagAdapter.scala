package io.strongtyped.funcqrs.akka

import akka.persistence.journal.{Tagged, WriteEventAdapter}
import io.strongtyped.funcqrs.{MetadataFacet, DomainEvent}

class DomainEventTagAdapter extends WriteEventAdapter {

  def manifest(event: Any): String = ""

  def toJournal(event: Any): Any = {
    event match {

      case evt: DomainEvent with MetadataFacet =>
        if (evt.metadata.tags.nonEmpty) tag(evt)
        else evt

      case _ => event
    }
  }

  private def tag(evt: DomainEvent with MetadataFacet): Tagged = {
    val tags = evt.metadata.tags.map(_.value)
    Tagged(evt, tags)
  }
}
