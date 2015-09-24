package fun.cqrs

import scala.concurrent.{ExecutionContext, Future}

trait Behavior[A <: Aggregate] {

  type Protocol = A#Protocol

  type Events = Seq[Protocol#ProtocolEvent]

  def validate(cmd: Protocol#ProtocolCommand)(implicit ec: ExecutionContext): Future[Protocol#ProtocolEvent]

  def validate(aggregate: A, cmd: Protocol#ProtocolCommand)(implicit ec: ExecutionContext): Future[Events]

  def applyEvent(evt: Protocol#ProtocolEvent): A

  def applyEvent(model: A, evt: Protocol#ProtocolEvent): A

  final def create(event: Protocol#ProtocolEvent): A = applyEvent(event)

  final def update(model: A, event: Protocol#ProtocolEvent): A = applyEvent(model, event)

  /**
   * Apply a list of events to a Aggregate
   * @return the updated Aggregate
   */
  private final def update(model: A, events: Events): A =
    events.foldLeft(model)(applyEvent)


  def applyCommand(cmd: Protocol#ProtocolCommand)(implicit ec: ExecutionContext): Future[(Protocol#ProtocolEvent, A)] = {
    validate(cmd).map { event => (event, create(event)) }
  }

  def applyCommand(aggregate: A, cmd: Protocol#ProtocolCommand)(implicit ec: ExecutionContext): Future[(Events, A)] = {
    validate(aggregate, cmd).map { events => (events, update(aggregate, events)) }
  }
}
