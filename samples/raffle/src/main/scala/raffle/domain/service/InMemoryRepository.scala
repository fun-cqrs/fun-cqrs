package raffle.domain.service

import scala.util.{ Success, Try }

trait InMemoryRepository {

  type Model
  type Identifier

  private var store: Map[Identifier, Model] = Map()

  def find(id: Identifier): Try[Model] = Try(store(id))

  def save(model: Model): Try[Unit] = {
    store = store + ($id(model) -> model)
    Success(())
  }

  def deleteById(id: Identifier): Unit = store = store - id

  def updateById(id: Identifier)(updateFunc: (Model) => Model): Try[Model] =
    find(id).map { model =>
      val updated = updateFunc(model)
      save(updated)
      updated
    }

  def fetchAll: Seq[Model] =
    store.values.toSeq

  /** Extract id from Model */
  protected def $id(model: Model): Identifier

}
