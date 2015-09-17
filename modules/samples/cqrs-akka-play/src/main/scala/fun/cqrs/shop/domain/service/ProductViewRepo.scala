package fun.cqrs.shop.domain.service

import fun.cqrs.InMemoryRepository
import fun.cqrs.shop.domain.model.{ProductId, ProductView}

class ProductViewRepo extends InMemoryRepository {

  type Identifier = ProductId
  type Model = ProductView

  /** Extract id van Model */
  protected def $id(model: ProductView): ProductId = model.identifier
}
