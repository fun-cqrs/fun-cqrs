package invoices.domain.model

import fun.cqrs.Repository

trait CustomerViewRepo extends Repository {
  type Model = CustomerView
  type Identifier = CustomerId
}
