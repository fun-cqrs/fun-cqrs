package io.funcqrs

import io.funcqrs.interpreters.Identity

import scala.concurrent.Future
import scala.language.higherKinds
import scala.util.Try

package object backend {

    val identityApi = api[Identity]
    val asyncApi = api[Future]
    val tryApi = api[Try]

    def api[F[_]] = new Api[F] {}
}
