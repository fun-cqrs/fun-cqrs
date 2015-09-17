package fun.cqrs

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

trait InMemoryRepository extends Repository {

  private var store: Map[Identifier, Model] = Map()

  def find(id: Identifier)(implicit ec: ExecutionContext): Future[Model] = {
    Future.fromTry(Try(store(id)))
  }

  def save(model: Model)(implicit ec: ExecutionContext): Future[Unit] = {
    store = store + ($id(model) -> model)
    Future.successful(())
  }


  def updateById(id: Identifier)(updateFunc: (Model) => Model)(implicit ec: ExecutionContext): Future[Model] = {
    for {
      model <- find(id)
      updated = updateFunc(model)
      _ <- save(updated)
    } yield updated
  }


  def fetchAll(implicit ec: ExecutionContext): Future[Seq[Model]] = {
    Future.successful(store.values.toSeq)
  }

  /** Extract id van Model */
  protected def $id(model: Model): Identifier

}
