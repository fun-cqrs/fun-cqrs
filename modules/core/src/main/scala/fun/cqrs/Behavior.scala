package fun.cqrs

import scala.concurrent.{Future, ExecutionContext}

trait Behavior[A <: Aggregate] {

  type Protocol = A#Protocol

  def validate(cmd: Protocol#CreateCmd)(implicit ec: ExecutionContext): Future[Protocol#CreateEvent]

  def validate(aggregate: A, cmd: Protocol#UpdateCmd)(implicit ec: ExecutionContext): Future[Seq[Protocol#UpdateEvent]]

  def applyEvent(evt: Protocol#CreateEvent): A

  def applyEvent(model: A, evt: Protocol#UpdateEvent): A

  final def create(event: Protocol#CreateEvent): A = applyEvent(event)

  final def update(model: A, event: Protocol#UpdateEvent): A = applyEvent(model, event)

  /**
   * Apply a list of events to a Aggregate
   * @return the updated Aggregate
   */
  private final def update(model: A, updateEvents: Seq[Protocol#UpdateEvent]): A =
    updateEvents.foldLeft(model)(applyEvent)


  def applyCommand(cmd: Protocol#CreateCmd)(implicit ec: ExecutionContext): Future[(Protocol#CreateEvent, A)] = {
    validate(cmd).map { event => (event, create(event)) }
  }

  def applyCommand(aggregate: A, cmd: Protocol#UpdateCmd)(implicit ec: ExecutionContext): Future[(Seq[Protocol#UpdateEvent], A)] = {
    validate(aggregate, cmd).map { events => (events, update(aggregate, events)) }
  }
}
