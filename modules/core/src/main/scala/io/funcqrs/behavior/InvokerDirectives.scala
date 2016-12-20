package io.funcqrs.behavior

import io.funcqrs.{ DomainCommand, DomainEvent }

import scala.annotation.implicitNotFound
import scala.collection.immutable
import scala.concurrent.Future
import scala.language.higherKinds
import scala.util.Try

@implicitNotFound(msg = "Not found implicit  for {F}")
trait InvokerDirective[-F[_]] {
  def newInvoker[C, E](cmdHandler: (C) => F[E]): CommandHandlerInvoker[C, E]
}

object InvokerDirective {

  implicit val traversableDirective = new InvokerDirective[immutable.Seq] {
    def newInvoker[C, E](cmdHandler: (C) => immutable.Seq[E]): CommandHandlerInvoker[C, E] = {
      IdCommandHandlerInvoker(cmdHandler)
    }
  }

  implicit val optionDirective = new InvokerDirective[Option] {
    def newInvoker[C, E](cmdHandler: (C) => Option[E]): CommandHandlerInvoker[C, E] = {

      val handlerWithSeq: (C) => immutable.Seq[E] =
        (cmd: C) => cmdHandler(cmd).map { immutable.Seq(_) }.getOrElse { immutable.Seq() }

      IdCommandHandlerInvoker(handlerWithSeq)
    }
  }

  implicit val tryDirective = new InvokerDirective[Try] {
    def newInvoker[C, E](cmdHandler: (C) => Try[E]): CommandHandlerInvoker[C, E] = {
      val handlerWithSeq: (C) => Try[immutable.Seq[E]] = (cmd: C) => cmdHandler(cmd).map(immutable.Seq(_))
      TryCommandHandlerInvoker(handlerWithSeq)
    }
  }

  implicit val asyncDirective = new InvokerDirective[Future] {
    def newInvoker[C, E](cmdHandler: (C) => Future[E]): CommandHandlerInvoker[C, E] = {
      import scala.concurrent.ExecutionContext.Implicits.global
      // wrap single event in immutable.Seq
      val handlerWithSeq: (C) => Future[immutable.Seq[E]] = (cmd: C) => cmdHandler(cmd).map(immutable.Seq(_))
      FutureCommandHandlerInvoker(handlerWithSeq)
    }
  }

}

trait InvokerListDirective[-F[_]] {
  def newInvoker[C, E](cmdHandler: (C) => F[List[E]]): CommandHandlerInvoker[C, E]
}

object InvokerListDirective {

  implicit val optionListDirective = new InvokerListDirective[Option] {
    def newInvoker[C, E](cmdHandler: (C) => Option[List[E]]): CommandHandlerInvoker[C, E] = {
      val handlerWithSeq: (C) => immutable.Seq[E] = (cmd: C) => cmdHandler(cmd).getOrElse { immutable.Seq[E]() }
      IdCommandHandlerInvoker(handlerWithSeq)
    }
  }

  implicit val tryListDirective = new InvokerListDirective[Try] {
    def newInvoker[C, E](cmdHandler: (C) => Try[List[E]]): CommandHandlerInvoker[C, E] =
      TryCommandHandlerInvoker(cmdHandler)
  }

  implicit val asyncListDirective = new InvokerListDirective[Future] {
    def newInvoker[C, E](cmdHandler: (C) => Future[List[E]]): CommandHandlerInvoker[C, E] =
      FutureCommandHandlerInvoker(cmdHandler)
  }
}

trait InvokerSeqDirective[-F[_]] {
  def newInvoker[C, E](cmdHandler: (C) => F[immutable.Seq[E]]): CommandHandlerInvoker[C, E]
}

object InvokerSeqDirective {

  implicit val optionSeqDirective = new InvokerSeqDirective[Option] {
    def newInvoker[C, E](cmdHandler: (C) => Option[immutable.Seq[E]]): CommandHandlerInvoker[C, E] = {
      val handlerWithSeq: (C) => immutable.Seq[E] = (cmd: C) => cmdHandler(cmd).getOrElse { immutable.Seq[E]() }
      IdCommandHandlerInvoker(handlerWithSeq)
    }
  }

  implicit val tryTraversableDirective = new InvokerSeqDirective[Try] {
    def newInvoker[C, E](cmdHandler: (C) => Try[immutable.Seq[E]]): CommandHandlerInvoker[C, E] =
      TryCommandHandlerInvoker(cmdHandler)
  }

  implicit val asyncTraversableDirective = new InvokerSeqDirective[Future] {
    def newInvoker[C, E](cmdHandler: (C) => Future[immutable.Seq[E]]): CommandHandlerInvoker[C, E] =
      FutureCommandHandlerInvoker(cmdHandler)
  }
}
