package io.funcqrs.config

import io.funcqrs.projections.Projection
import io.funcqrs.projections.PublisherFactory
import scala.language.higherKinds
import scala.concurrent.Future

case class ProjectionConfig[O, E](
    projection: Projection[E],
    publisherFactory: PublisherFactory[O, E],
    name: String,
    offsetPersistenceStrategy: OffsetPersistenceStrategy[O] = NoOffsetPersistenceStrategy
) {

  def withoutOffsetPersistence(): ProjectionConfig[O, E] = {
    copy(offsetPersistenceStrategy = NoOffsetPersistenceStrategy)
  }

  @deprecated("use an explicit offset persistence strategy", "1.0.0")
  def withBackendOffsetPersistence()(implicit ev: O =:= Long): ProjectionConfig[O, E] = {
    copy(offsetPersistenceStrategy = BackendOffsetPersistenceStrategy(name))
  }

  def withCustomOffsetPersistence(strategy: CustomOffsetPersistenceStrategy[O]): ProjectionConfig[O, E] = {
    copy(offsetPersistenceStrategy = strategy)
  }

  def withOffsetPersistenceStrategy(strategy: CustomOffsetPersistenceStrategy[O]): ProjectionConfig[O, E] = {
    copy(offsetPersistenceStrategy = strategy)
  }

}

trait OffsetPersistenceStrategy[+O]

case object NoOffsetPersistenceStrategy extends OffsetPersistenceStrategy[Nothing]

case class BackendOffsetPersistenceStrategy[O](persistenceId: String) extends OffsetPersistenceStrategy[O]

trait CustomOffsetPersistenceStrategy[O] extends OffsetPersistenceStrategy[O] {

  def saveCurrentOffset(offset: O): Future[Unit]

  /** Returns the current offset as persisted in DB */
  def readOffset: Future[Option[O]]

}
