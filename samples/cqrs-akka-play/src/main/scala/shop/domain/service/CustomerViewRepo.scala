package shop.domain.service

import io.strongtyped.funcqrs.InMemoryRepository
import shop.domain.model.{ CustomerId, CustomerView }

class CustomerViewRepo extends InMemoryRepository {

  type Model = CustomerView
  type Identifier = CustomerId

  def $id(model: CustomerView): CustomerId = model.identifier
}
