package io.funcqrs

import io.funcqrs.projections.Projection

import scala.util.Failure

trait ProjectionFixture {

  sealed trait Event
  case class FooEvent(value: String) extends Event

  case class BarEvent(num: Int) extends Event

  def newFailingProjection = new Projection[Event] {
    def handleEvent = attempt.HandleEvent {
      case _ => Failure(new IllegalArgumentException("this projection should not receive events"))
    }
  }

  class FooProjection extends Projection[Event] {
    var result: Option[String] = None

    def handleEvent = sync.HandleEvent {
      case evt: FooEvent => result = Some(evt.value)
    }
  }

  def newFooProjection = new FooProjection

  class BarProjection extends Projection[Event] {
    var result: Option[Int] = None

    def handleEvent = sync.HandleEvent {
      case evt: BarEvent => result = Some(evt.num)
    }
  }

  def newBarProjection = new BarProjection
}
