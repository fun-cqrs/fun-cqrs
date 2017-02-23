package io.funcqrs

import io.funcqrs.Projection.{ AndThenProjection, OrElseProjection }

import scala.concurrent.Future
import scala.util.control.NonFatal

trait Projection {

  type ReceiveEvent  = PartialFunction[Any, Future[Unit]]
  type HandleFailure = PartialFunction[(Any, Throwable), Future[Unit]]

  def name: String = this.getClass.getSimpleName

  def receiveEvent: ReceiveEvent

  def onFailure: HandleFailure = PartialFunction.empty

  final def onEvent(evt: Any): Future[Unit] = {
    if (receiveEvent.isDefinedAt(evt)) {
      import scala.concurrent.ExecutionContext.Implicits.global
      receiveEvent(evt).recoverWith {
        case NonFatal(exp) if onFailure.isDefinedAt((evt, exp)) => onFailure(evt, exp)
      }
    } else {
      Future.successful(())
    }
  }

  /**
    * Builds a [[AndThenProjection]] composed of this Projection and the passed Projection.
    *
    * Events will be send to both projections. One after the other starting by this followed by the passed Projection.
    *
    * NOTE: In the occurrence of any failure on any of the underling Projections, this Projection may be replayed,
    * therefore idempotent operations are recommended.
    */
  def andThen(projection: Projection) = new AndThenProjection(this, projection)

  /**
    * Builds a [[OrElseProjection]]composed of this Projection and the passed Projection.
    *
    * If this Projection is defined for a given incoming Event, then this Projection will be applied,
    * otherwise we fallback to the passed Projection.
    */
  def orElse(fallbackProjection: Projection) = new OrElseProjection(this, fallbackProjection)
}

object Projection {

  /** Projection with empty domain */
  def empty = new Projection {
    def receiveEvent: ReceiveEvent = PartialFunction.empty
  }

  /**
    * A [[Projection]] composed of two other Projections to each Event will be sent.
    *
    * Note that the second Projection is only applied once the first is completed successfully.
    *
    * In the occurrence of any failure on any of the underling Projections, this Projection may be replayed,
    * therefore idempotent operations are recommended.
    *
    * If none of the underlying Projections is defined for a given DomainEvent,
    * then this Projection is considered to be not defined for this specific DomainEvent.
    * As such a [[AndThenProjection]] can be combined with a [[OrElseProjection]].
    *
    * For example:
    * {{{
    *   val projection1 : Projection = ...
    *   val projection2 : Projection = ...
    *   val projection3 : Projection = ...
    *
    *   val finalProjection = (projection1 andThen projection2) orElse projection3
    *
    *   finalProjection.onEvent(SomeEvent("abc"))
    *   // if SomeEvent("abc") is not defined for projection1 nor for projection2, projection3 will be applied
    * }}}
    *
    */
  private[funcqrs] class AndThenProjection(firstProj: Projection, secondProj: Projection)
      extends ComposedProjection(firstProj, secondProj)
      with Projection {

    import scala.concurrent.ExecutionContext.Implicits.global

    val projections = Seq(firstProj, secondProj)

    override def name: String = s"${firstProj.name}-and-then-${secondProj.name}"

    def receiveEvent: ReceiveEvent = {
      // note that we only broadcast if at least one of the underlying
      // projections is defined for the incoming event
      // as such we make it possible to compose using orElse
      case domainEvent if composedHandleEvent.isDefinedAt(domainEvent) =>
        // send event to all projections
        firstProj.onEvent(domainEvent).flatMap { _ =>
          secondProj.onEvent(domainEvent)
        }
    }
  }

  /**
    * A [[Projection]] composed of two other Projections.
    *
    * Its `receiveEvent` is defined in terms of the `receiveEvent` method form the first Projection
    * with fallback to the `receiveEvent` method of the second Projection.
    *
    * As such the second Projection is only applied if the first Projection is not defined
    * for the given incoming Events
    *
    */
  private[funcqrs] class OrElseProjection(firstProj: Projection, secondProj: Projection)
      extends ComposedProjection(firstProj, secondProj)
      with Projection {
    override def name: String = s"${firstProj.name}-or-then-${secondProj.name}"

    def receiveEvent = composedHandleEvent
  }

  private[funcqrs] class ComposedProjection(firstProj: Projection, secondProj: Projection) {
    // compose underlying receiveEvents PartialFunction in order
    // to decide if this Projection is defined for given incoming DomainEvent
    private[funcqrs] def composedHandleEvent = firstProj.receiveEvent orElse secondProj.receiveEvent
  }

}
