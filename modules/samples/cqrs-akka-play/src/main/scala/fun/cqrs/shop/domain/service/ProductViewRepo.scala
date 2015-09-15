package fun.cqrs.shop.domain.service

import fun.cqrs.InMemoryRepository
import fun.cqrs.shop.domain.model.{ProductView, ProductId, Product}

class ProductViewRepo extends InMemoryRepository {

  type Identifier = ProductId
  type Model = ProductView

  /** Extract id van Model */
  def $id(model: ProductView): ProductId = ???
}
