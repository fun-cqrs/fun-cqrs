package io.funcqrs

import scala.concurrent.{ExecutionContext, Future}

trait Repository {

  type Model
  type Identifier

  def find(id: Identifier)(implicit ec: ExecutionContext): Future[Model]

  def save(model: Model)(implicit ec: ExecutionContext): Future[Unit]

  def updateById(id: Identifier)(updateFunc: Model => Model)(implicit ec: ExecutionContext): Future[Model]

  def updateByIdAsync(id: Identifier)(updateFunc: Model => Future[Model])(implicit ec: ExecutionContext): Future[Model]

  def fetchAll(implicit ec: ExecutionContext): Future[Seq[Model]]

  def deleteById(id: Identifier): Future[Unit]
}
