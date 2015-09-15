package fun.cqrs.shop.domain.model

case class ProductView(name: String, description: String, price: Double, identifier: ProductId)
