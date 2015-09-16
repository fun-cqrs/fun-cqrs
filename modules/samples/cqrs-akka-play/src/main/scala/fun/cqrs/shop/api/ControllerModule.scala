package fun.cqrs.shop.api

import com.softwaremill.macwire._
import fun.cqrs.shop.domain.service.ProductModule

trait ControllerModule extends ProductModule with AkkaModule {

  val productController = wire[ProductController]
}