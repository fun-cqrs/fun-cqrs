package io.funcqrs

package object interpreters {

  /**
    * Convenient type alias to make any identity instances well-kinded.
    *
    * This allow us to use plain values whenever we need higher-kind of one type parameter.
    *
    * For example:
    * {{{
    *  trait Handlers[F[_]] {
    *    def handleCommand(cmd:Command): F[Event]
    *  }
    *
    *  // implementations of handler can define the type of F
    *  object TryHandler extends Handlers[Try] {
    *    def handleCommand(cmd:Command): Try[Event] = Try(someEvent())
    *
    *    def someEvent() : Event = ...
    *  }
    *
    *  object IdentityHandler extends Handlers[Identity] {
    *    // since Identity[Event] = Event, we can call someEvent() directly without wrapping it!
    *    def handleCommand(cmd:Command): Identity[Event] = someEvent()
    *
    *    def someEvent() : Event = ...
    *  }
    * }}}
    */
  type Identity[T] = T
}
