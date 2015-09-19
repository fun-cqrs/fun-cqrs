package shop.domain.service

import fun.cqrs.InMemoryRepository
import shop.domain.model.{ProductNumber, OrderNumber, OrderView}

class OrderViewRepo extends InMemoryRepository {


  def findByProduct(number: ProductNumber) = {
    ???
  }


  type Identifier = OrderNumber
  type Model = OrderView

  protected def $id(model: OrderView): OrderNumber = model.number

}
