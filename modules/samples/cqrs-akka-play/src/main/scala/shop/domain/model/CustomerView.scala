package shop.domain.model

case class CustomerView(name: String,
                        street: Street,
                        city: City,
                        country: Country,
                        vatNumber: Option[VAT],
                        identifier: CustomerId)
