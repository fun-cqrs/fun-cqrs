package fun.cqrs.shop.domain.service

import fun.cqrs.InMemoryRepository
import fun.cqrs.shop.domain.model.ProductId
import fun.cqrs.shop.domain.model.Product

class ProductRepo extends InMemoryRepository {

  /** Extract id van Model */
  def $id(product: Product): ProductId = product.identifier

  type Identifier = ProductId
  type Model = Product
}
