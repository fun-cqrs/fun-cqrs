package io.funcqrs.config

import io.funcqrs.backend.Query

import scala.concurrent.Future
import io.funcqrs.projections.Projection

case class ProjectionConfig(
    query: Query,
    projection: Projection,
    name: String,
    offsetPersistenceStrategy: OffsetPersistenceStrategy = NoOffsetPersistenceStrategy
) {

  def withoutOffsetPersistence(): ProjectionConfig = {
    copy(offsetPersistenceStrategy = NoOffsetPersistenceStrategy)
  }

  def withBackendOffsetPersistence(): ProjectionConfig = {
    copy(offsetPersistenceStrategy = BackendOffsetPersistenceStrategy(name))
  }

  def withCustomOffsetPersistence(strategy: CustomOffsetPersistenceStrategy): ProjectionConfig = {
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
