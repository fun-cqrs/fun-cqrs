package raffle.akka.persistence

import akka.persistence.journal.{ Tagged, WriteEventAdapter }
import raffle.domain.model.{ Raffle, RaffleEvent }

class TagWriteEventAdapter extends WriteEventAdapter {

  def manifest(event: Any): String = ""

  def toJournal(event: Any): Any = {
    event match {
      // all lottery events get tagged with lottery tag!
      case evt: RaffleEvent => Tagged(evt, Set(Raffle.tag.value))
      case evt              => evt
    }
  }
}
