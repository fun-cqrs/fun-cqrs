package shop.domain.model

import play.api.libs.json.Json

case class CustomerView(name: String,
                        street: Option[Street],
                        city: Option[City],
                        country: Option[Country],
                        vatNumber: Option[VAT],
                        identifier: CustomerId)

object CustomerView {
  implicit val format = Json.writes[CustomerView]
}