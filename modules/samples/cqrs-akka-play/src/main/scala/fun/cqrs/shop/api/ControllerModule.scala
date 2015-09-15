package fun.cqrs.shop.api

import com.softwaremill.macwire._

trait ControllerModule {

  val productController = wire[ProductController]
}