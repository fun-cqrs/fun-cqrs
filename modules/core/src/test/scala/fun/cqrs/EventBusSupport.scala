package fun.cqrs

trait EventBusSupport {

  val eventBus = new EventBus
  def addHandler(handler: HandleEvent) = eventBus.addHandler(handler)

}

class EventBus {

  private var handlers = List[HandleEvent]()

  def publishEvents[E <: DomainEvent](events: Seq[E]): Seq[E] = {
    events foreach publishEvent
    events
  }

  def publishEvent[E <: DomainEvent](event: E): E = {
    handlers.foreach { handler =>
      if (handler.isDefinedAt(event)) handler(event)
    }
    // return current event
    event
  }

  def addHandler(handler: HandleEvent) = {
    handlers = handlers :+ handler
  }
}