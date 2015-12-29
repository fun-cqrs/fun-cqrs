package io.funcqrs.interpreters

import scala.concurrent.Future
import scala.language.higherKinds
import scala.util.Try

/**
  * Provides type-classes for map and flatMap over [[Identity]], [[Try]] and [[Future]]
  *
  * This implementation does NOT pretend sound. We only an abstraction to map and flatMap
  * over  [[Identity]], [[Try]] and [[Future]] in a unified way.
  */
object Monads {

  trait MonadOps[F[_]] {
    def map[A, B](fa: F[A])(f: A => B): F[B]
    def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B]
  }

  implicit val identityMonad: MonadOps[Identity] = new MonadOps[Identity] {
    def map[A, B](fa: Identity[A])(f: (A) => B): Identity[B] = f(fa)
    def flatMap[A, B](fa: Identity[A])(f: (A) => Identity[B]): Identity[B] = f(fa)
  }

  implicit val tryMonad: MonadOps[Try] = new MonadOps[Try] {
    def map[A, B](fa: Try[A])(f: (A) => B): Try[B] = fa.map(f)
    def flatMap[A, B](fa: Try[A])(f: (A) => Try[B]): Try[B] = fa.flatMap(f)
  }

  implicit val futureMonad: MonadOps[Future] = new MonadOps[Future] {
    import scala.concurrent.ExecutionContext.Implicits.global
    def map[A, B](fa: Future[A])(f: (A) => B): Future[B] = fa.map(f)
    def flatMap[A, B](fa: Future[A])(f: (A) => Future[B]): Future[B] = fa.flatMap(f)
  }

  trait Monad[A, F[_]] {
    def map[B](f: A => B): F[B]
    def flatMap[B](f: A => F[B]): F[B]
  }

  /** Builds a 'Monad' for whatever F[_] having a MonadOps type-class in the implicit scope  */
  def monad[A, F[_]](fa: F[A])(implicit monadOps: MonadOps[F]) =

    new Monad[A, F] {

      def map[B](f: A => B): F[B] = {
        monadOps.map(fa)(f)
      }

      def flatMap[B](f: A => F[B]): F[B] = {
        monadOps.flatMap(fa)(f)
      }
    }
}
