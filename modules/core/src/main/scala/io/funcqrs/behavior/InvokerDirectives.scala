package io.funcqrs.behavior

import io.funcqrs.{ DomainCommand, DomainEvent }

import scala.collection.immutable
import scala.concurrent.Future
import scala.language.higherKinds
import scala.util.Try

trait InvokerDirective[-F[_]] {
  def newInvoker[C <: DomainCommand, E <: DomainEvent](cmdHandler: (C) => F[E]): CommandHandlerInvoker[C, E]
}

object InvokerDirective {

  implicit val traversableDirective = new InvokerDirective[immutable.Seq] {
    def newInvoker[C <: DomainCommand, E <: DomainEvent](cmdHandler: (C) => immutable.Seq[E]): CommandHandlerInvoker[C, E] = {
      IdCommandHandlerInvoker(cmdHandler)
    }
  }

  implicit val tryDirective = new InvokerDirective[Try] {
    def newInvoker[C <: DomainCommand, E <: DomainEvent](cmdHandler: (C) => Try[E]): CommandHandlerInvoker[C, E] = {
      val handlerWithSeq: (C) => Try[immutable.Seq[E]] = (cmd: C) => cmdHandler(cmd).map(immutable.Seq(_))
      TryCommandHandlerInvoker(handlerWithSeq)
    }
  }

  implicit val asyncDirective = new InvokerDirective[Future] {
    def newInvoker[C <: DomainCommand, E <: DomainEvent](cmdHandler: (C) => Future[E]): CommandHandlerInvoker[C, E] = {
      import scala.concurrent.ExecutionContext.Implicits.global
      // wrap single event in immutable.Seq
      val handlerWithSeq: (C) => Future[immutable.Seq[E]] = (cmd: C) => cmdHandler(cmd).map(immutable.Seq(_))
      FutureCommandHandlerInvoker(handlerWithSeq)
    }
  }

}

trait InvokerListDirective[-F[_]] {
  def newInvoker[C <: DomainCommand, E <: DomainEvent](cmdHandler: (C) => F[List[E]]): CommandHandlerInvoker[C, E]
}

object InvokerListDirective {

  implicit val tryListDirective = new InvokerListDirective[Try] {
    def newInvoker[C <: DomainCommand, E <: DomainEvent](cmdHandler: (C) => Try[List[E]]): CommandHandlerInvoker[C, E] =
      TryCommandHandlerInvoker(cmdHandler)
  }

  implicit val asyncListDirective = new InvokerListDirective[Future] {
    def newInvoker[C <: DomainCommand, E <: DomainEvent](cmdHandler: (C) => Future[List[E]]): CommandHandlerInvoker[C, E] =
      FutureCommandHandlerInvoker(cmdHandler)
  }
}

trait InvokerSeqDirective[-F[_]] {
  def newInvoker[C <: DomainCommand, E <: DomainEvent](cmdHandler: (C) => F[immutable.Seq[E]]): CommandHandlerInvoker[C, E]
}

object InvokerSeqDirective {

  implicit val tryTraversableDirective = new InvokerSeqDirective[Try] {
    def newInvoker[C <: DomainCommand, E <: DomainEvent](cmdHandler: (C) => Try[immutable.Seq[E]]): CommandHandlerInvoker[C, E] =
      TryCommandHandlerInvoker(cmdHandler)
  }

  implicit val asyncTraversableDirective = new InvokerSeqDirective[Future] {
    def newInvoker[C <: DomainCommand, E <: DomainEvent](cmdHandler: (C) => Future[immutable.Seq[E]]): CommandHandlerInvoker[C, E] =
      FutureCommandHandlerInvoker(cmdHandler)
  }
}