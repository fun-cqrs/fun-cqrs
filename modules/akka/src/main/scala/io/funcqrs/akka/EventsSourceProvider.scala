package io.funcqrs.akka

import akka.actor.ActorContext
import akka.stream.scaladsl.Source
import io.funcqrs.backend.{ EventsPublisherProvider, EventEnvelope }
import org.reactivestreams.{Subscriber, Publisher}

/**
 * Provides an Akka-Streams [[Source]] that produces [[EventEnvelope]]s.
 * TODO: document it with implementation example
 */
trait EventsSourceProvider {

  def source(offset: Long)(implicit context: ActorContext): Source[EventEnvelope, Unit]

}


object EventsSourceProvider {

  def fromPublisher(publisherProvider: EventsPublisherProvider) = {
    new EventsSourceProvider {
      def source(offset: Long)(implicit context: ActorContext): Source[EventEnvelope, Unit] = {
        Source(publisherProvider.publisher(offset))
      }
    }
  }

}