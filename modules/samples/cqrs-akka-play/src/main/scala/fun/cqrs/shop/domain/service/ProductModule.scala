package fun.cqrs.shop.domain.service

import akka.actor.{ActorRef, Props}
import com.softwaremill.macwire._
import fun.cqrs.shop.api.AkkaModule
import fun.cqrs.shop.domain.model.Product


trait ProductModule {
  this: AkkaModule =>


  lazy val productAggregateManager: ActorRef @@ Product.type =
    actorSystem
      .actorOf(Props(classOf[ProductAggregateManager]), "productAggregateManager")
      .taggedWith[Product.type]
}
