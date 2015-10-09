package io.strongtyped.funcqrs

import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

trait InMemoryRepository extends Repository with LazyLogging {

  private var store: Map[Identifier, Model] = Map()

  def find(id: Identifier)(implicit ec: ExecutionContext): Future[Model] = {
    Future.fromTry(Try(store(id)))
  }

  def save(model: Model)(implicit ec: ExecutionContext): Future[Unit] = {
    logger.debug(s"saving $model")
    store = store + ($id(model) -> model)
    Future.successful(())
  }

  def deleteById(id: Identifier): Future[Unit] = {
    store = store.filterKeys(_ != id)
    Future.successful(())
  }

  def updateById(id: Identifier)(updateFunc: (Model) => Model)(implicit ec: ExecutionContext): Future[Model] = {
    updateByIdAsync(id) { model =>
      Future.fromTry(Try(updateFunc(model)))
    }
  }

  def updateByIdAsync(id: Identifier)(updateFunc: (Model) => Future[Model])(implicit ec: ExecutionContext): Future[Model] = {
    for {
      model <- find(id)
      updated <- updateFunc(model)
      _ <- save(updated)
    } yield {
      logger.debug(s"updated $updated")
      updated
    }
  }

  def fetchAll(implicit ec: ExecutionContext): Future[Seq[Model]] = {
    val all = store.values.toSeq
    all.foreach { s => logger.debug(s"found item $s") }
    Future.successful(store.values.toSeq)
  }

  /** Extract id van Model */
  protected def $id(model: Model): Identifier

}
