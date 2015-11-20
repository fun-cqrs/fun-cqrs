package io.funcqrs

package object akka {

  @deprecated("Renamed to EventsSourceProvider to better express its responsibility. ")
  type ProjectionSource = EventsSourceProvider
}
