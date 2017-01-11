package io.funcqrs.config

import io.funcqrs.backend.Query

import scala.concurrent.Future
import io.funcqrs.projections.Projection

case class ProjectionConfig[E](
    query: Query,
    projection: Projection[E],
    name: String,
    offsetPersistenceStrategy: OffsetPersistenceStrategy = NoOffsetPersistenceStrategy
) {

  def withoutOffsetPersistence(): ProjectionConfig[E] = {
    copy(offsetPersistenceStrategy = NoOffsetPersistenceStrategy)
  }

  def withBackendOffsetPersistence(): ProjectionConfig[E] = {
    copy(offsetPersistenceStrategy = BackendOffsetPersistenceStrategy(name))
  }

  def withCustomOffsetPersistence(strategy: CustomOffsetPersistenceStrategy): ProjectionConfig[E] = {
    copy(offsetPersistenceStrategy = strategy)
  }

}

trait OffsetPersistenceStrategy

case object NoOffsetPersistenceStrategy extends OffsetPersistenceStrategy

case class BackendOffsetPersistenceStrategy(persistenceId: String) extends OffsetPersistenceStrategy

trait CustomOffsetPersistenceStrategy extends OffsetPersistenceStrategy {

  def saveCurrentOffset(offset: Long): Future[Unit]

  /** Returns the current offset as persisted in DB */
  def readOffset: Future[Option[Long]]

}
