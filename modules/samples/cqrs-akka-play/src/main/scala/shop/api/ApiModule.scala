package shop.api

import com.softwaremill.macwire._
import shop.domain.service.ServiceModule

trait ApiModule extends ServiceModule {

  val productController = wire[ProductController]
  val productViewController = wire[ProductViewController]
}
