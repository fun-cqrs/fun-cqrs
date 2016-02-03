package io.funcqrs.dsl

import io.funcqrs.interpreters._
import io.funcqrs.{ AggregateAliases, AggregateLike }

import scala.collection.immutable
import scala.concurrent.Future
import scala.language.higherKinds
import scala.reflect.ClassTag
import scala.util.Try

trait Binding[A <: AggregateLike] extends AggregateAliases {

  type Aggregate = A

  def cmdHandlerInvokers: CommandToInvoker[A#Command, A#Event]
  def eventListeners: EventToAggregate[A#Event, A]

  def reject(cmdHandler: PartialFunction[Command, Throwable]): Binding[Aggregate]
  def rejectCommand(cmdHandler: PartialFunction[Command, Throwable]): Binding[Aggregate] = reject(cmdHandler)

  def handler[C <: Command: ClassTag, E <: Event](cmdHandler: C => Identity[E]): Binding[Aggregate]
  def commandHandler[C <: Command: ClassTag, E <: Event](cmdHandler: C => Identity[E]): Binding[Aggregate] = handler(cmdHandler)

  def handler: ManyEventsBinder[Identity]
  def commandHandler: ManyEventsBinder[Identity] = handler

  def tryHandler[C <: Command: ClassTag, E <: Event](cmdHandler: C => Try[E]): Binding[Aggregate]
  def tryCommandHandler[C <: Command: ClassTag, E <: Event](cmdHandler: C => Try[E]): Binding[Aggregate] = tryHandler(cmdHandler)

  def tryHandler: ManyEventsBinder[Try]
  def tryCommandHandler: ManyEventsBinder[Try] = tryHandler

  def asyncHandler[C <: Command: ClassTag, E <: Event](cmdHandler: C => Future[E]): Binding[Aggregate]
  def asyncCommandHandler[C <: Command: ClassTag, E <: Event](cmdHandler: C => Future[E]): Binding[Aggregate] = asyncHandler(cmdHandler)

  def asyncHandler: ManyEventsBinder[Future]
  def asyncCommandHandler: ManyEventsBinder[Future] = asyncHandler

  def listener[E <: Event: ClassTag](eventListener: E => A): Binding[Aggregate]
  def eventListener[E <: Event: ClassTag](eventListener: E => A): Binding[Aggregate] = listener(eventListener)

  trait ManyEventsBinder[F[_]] {
    def manyEvents[C <: Command: ClassTag, E <: Event](cmdHandler: C => F[immutable.Seq[E]]): Binding[Aggregate]
  }

}

