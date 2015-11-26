package io.funcqrs.akka

import akka.persistence.journal.{Tagged, WriteEventAdapter}
import io.funcqrs.{DomainEvent, MetadataFacet}

class DomainEventTagAdapter extends WriteEventAdapter {

  def manifest(event: Any): String = ""

  def toJournal(event: Any): Any = {
    event match {

      case evt: DomainEvent with MetadataFacet[_] =>
        if (evt.tags.nonEmpty) tag(evt)
        else evt

      case _ => event
    }
  }

  private def tag(evt: DomainEvent with MetadataFacet[_]): Tagged = {
    val tags = evt.tags.map(_.value)
    Tagged(evt, tags)
  }
}
