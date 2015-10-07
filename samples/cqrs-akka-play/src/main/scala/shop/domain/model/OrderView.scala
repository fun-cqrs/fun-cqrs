package shop.domain.model

import play.api.libs.json.{JsString, JsValue, Writes, Json}

case class OrderView(number: OrderNumber,
                     customerName: String,
                     items: List[OrderItem] = List(),
                     total: Double = 0.0,
                     status: Status = Open) {

  def addItem(newItem: OrderItem): OrderView = {

    val maybeItem = items.find(_.productNumber == newItem.productNumber)

    val updatedItem =
      maybeItem match {
        case Some(item) => item.plusOne
        case None       => newItem
      }

    val removed = items.filter(_.productNumber != newItem.productNumber)
    val updatedList = removed :+ updatedItem
    copy(items = updatedList).recalculateTotal()
  }

  def removeItem(toRemove: ProductNumber): OrderView = {
    val maybeItem = items.find(_.productNumber == toRemove)

    val updatedList =
      maybeItem match {

        // if has more than one, decrease number
        case Some(item) if item.quantity.num > 1 =>
          val removed = items.filter(_.productNumber != toRemove)
          removed :+ item.minusOne

        // for all other case we must make sure that item is not on list anymore
        case _ => items.filter(_.productNumber != toRemove)
      }

    copy(items = updatedList).recalculateTotal()
  }

  def recalculateTotal(): OrderView = {
    val total =
      this.items.map { item =>
        item.price * item.quantity.num
      }.sum
    copy(total = total)
  }
}

case class OrderItem(productNumber: ProductNumber, name: String, price: Double, quantity: Quantity) {
  def plusOne: OrderItem = copy(quantity = this.quantity.plusOne)
  def minusOne: OrderItem = copy(quantity = this.quantity.minusOne)
}

object OrderView {

  implicit val writerStatus = new Writes[Status] {
    def writes(status: Status): JsValue = {
      status match {
        case Open      => JsString("OPEN")
        case Executed  => JsString("EXECUTED")
        case Cancelled => JsString("CANCELLED")
      }
    }
  }
  implicit val writerItem = Json.writes[OrderItem]
  implicit val writerOrderView = Json.writes[OrderView]
}