package io.funcqrs.akka

import akka.persistence.journal.{ Tagged, WriteEventAdapter }
import io.funcqrs.{ DomainEvent, MetadataFacet }

@deprecated(
  message =
    "This will be removed together with DomainEvent. You should define your own event adapter instead an wire it in your config file.",
  since = "1.0.0"
)
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
