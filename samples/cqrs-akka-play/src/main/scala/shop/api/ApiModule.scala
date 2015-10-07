package shop.api

import com.softwaremill.macwire._
import shop.domain.service.ServiceModule

trait ApiModule extends ServiceModule {

  val productCmdController = wire[ProductCmdController]
  val productQueryController = wire[ProductQueryController]

  val customerCmdController = wire[CustomerCmdController]
  val customerQueryController = wire[CustomerQueryController]

  val orderCmdController = wire[OrderCmdController]
  val orderQueryController = wire[OrderQueryController]
}
