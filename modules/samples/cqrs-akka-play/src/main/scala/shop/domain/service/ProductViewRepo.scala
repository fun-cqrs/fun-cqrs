package shop.domain.service

import fun.cqrs.InMemoryRepository
import shop.domain.model.{ProductNumber, ProductView}

class ProductViewRepo extends InMemoryRepository {

  type Identifier = ProductNumber
  type Model = ProductView

  /** Extract id van Model */
  protected def $id(model: ProductView): ProductNumber = model.identifier
}
