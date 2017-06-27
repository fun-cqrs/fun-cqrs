package io.funcqrs.akka

import akka.persistence.journal.{ Tagged, WriteEventAdapter }
import io.funcqrs.akka.TestModel.User

class TaggingEventAdapter extends WriteEventAdapter {

  override def manifest(event: Any): String = event.getClass.getName

  override def toJournal(event: Any): Any = {
    event match {
      case evt: UserEvt => Tagged(evt, Set(User.tag))
      case evt          => evt
    }
  }

}
