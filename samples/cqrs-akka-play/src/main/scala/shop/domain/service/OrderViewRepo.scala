package shop.domain.service

import io.strongtyped.funcqrs.InMemoryRepository
import shop.domain.model.{OrderNumber, OrderView}

class OrderViewRepo extends InMemoryRepository {

  type Identifier = OrderNumber
  type Model = OrderView

  protected def $id(model: OrderView): OrderNumber = model.number

}
