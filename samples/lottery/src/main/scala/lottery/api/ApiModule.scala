package lottery.api

import com.softwaremill.macwire._
import lottery.domain.service.ServiceModule

trait ApiModule extends ServiceModule {

  val productCmdController = wire[LotteryCmdController]
  val productQueryController = wire[LotteryQueryController]

}
