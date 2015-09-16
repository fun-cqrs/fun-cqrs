package fun.cqrs.shop.domain.service

import akka.actor.Props
import fun.cqrs.akka.{AggregateActor, AggregateManager}
import fun.cqrs.shop.domain.model.{Product, ProductId}

class ProductAggregateManager extends AggregateManager[Product] {

  def generateId: ProductId = ProductId()

  /**
   * Build Props for a new Aggregate Actor with the passed Id
   */
  def aggregateActorProps(id: ProductId): Props = {

    // NOTE: behavior needs a ExecutionContext and we don't want to pass context.dispatcher
    import scala.concurrent.ExecutionContext.Implicits.global

    Props(classOf[AggregateActor[Product]], id, Product.behavior(id))
  }

}

