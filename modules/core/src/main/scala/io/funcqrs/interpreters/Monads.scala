package io.funcqrs.interpreters

import scala.concurrent.Future
import scala.language.higherKinds
import scala.util.Try

object Monads {

  trait Monad[F[_]] {
    def map[A, B](fa: F[A])(f: A => B): F[B]
    def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B]
  }

  implicit val identityMonad: Monad[Identity] = new Monad[Identity] {
    def map[A, B](fa: Identity[A])(f: (A) => B): Identity[B] = f(fa)
    def flatMap[A, B](fa: Identity[A])(f: (A) => Identity[B]): Identity[B] = f(fa)
  }

  implicit val tryMonad: Monad[Try] = new Monad[Try] {
    def map[A, B](fa: Try[A])(f: (A) => B): Try[B] = fa.map(f)
    def flatMap[A, B](fa: Try[A])(f: (A) => Try[B]): Try[B] = fa.flatMap(f)
  }

  implicit val futureMonad: Monad[Future] = new Monad[Future] {
    import scala.concurrent.ExecutionContext.Implicits.global
    def map[A, B](fa: Future[A])(f: (A) => B): Future[B] = fa.map(f)
    def flatMap[A, B](fa: Future[A])(f: (A) => Future[B]): Future[B] = fa.flatMap(f)
  }
}
