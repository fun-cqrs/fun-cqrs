package io.funcqrs.interpreters

import scala.concurrent.Future
import scala.language.higherKinds
import scala.util.Try

/**
 * Provides type-classes for map and flatMap over [[Identity]], [[Try]] and [[Future]]
 *
 * This implementation does NOT pretend to sound. We only need an abstraction to map and flatMap
 * over [[Identity]], [[Try]] and [[Future]] in a unified way.
 */
object Monads {

  trait MonadOps[F[_]] {
    def pure[A](any: A): F[A]

    def map[A, B](fa: F[A])(f: A => B): F[B]

    def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B]
  }

  object MonadOps {
    def apply[F[_]: MonadOps]: MonadOps[F] = implicitly[MonadOps[F]]
  }

  implicit val identityMonad: MonadOps[Identity] = new MonadOps[Identity] {
    def pure[A](any: A): Identity[A] = any

    def map[A, B](fa: Identity[A])(f: (A) => B): Identity[B] = f(fa)

    def flatMap[A, B](fa: Identity[A])(f: (A) => Identity[B]): Identity[B] = f(fa)
  }

  implicit val tryMonad: MonadOps[Try] = new MonadOps[Try] {
    def pure[A](any: A): Try[A] = Try(any)

    def map[A, B](fa: Try[A])(f: (A) => B): Try[B] = fa.map(f)

    def flatMap[A, B](fa: Try[A])(f: (A) => Try[B]): Try[B] = fa.flatMap(f)

  }

  implicit val futureMonad: MonadOps[Future] = new MonadOps[Future] {

    import scala.concurrent.ExecutionContext.Implicits.global

    def pure[A](any: A): Future[A] = Future.successful(any)

    def map[A, B](fa: Future[A])(f: (A) => B): Future[B] = fa.map(f)

    def flatMap[A, B](fa: Future[A])(f: (A) => Future[B]): Future[B] = fa.flatMap(f)
  }

  trait Monad[A, F[_]] {
    def map[B](f: A => B): F[B]

    def flatMap[B](f: A => F[B]): F[B]
  }

  object Monad {
    def pure[A, F[_]: MonadOps](any: A): F[A] = {
      MonadOps[F].pure(any)
    }
  }

  implicit class MonadSyntax[F[_]: MonadOps, A](fa: F[A]) {
    def map[B](f: A => B): F[B] = MonadOps[F].map(fa)(f)

    def flatMap[B](f: A => F[B]): F[B] = MonadOps[F].flatMap(fa)(f)

  }
}
