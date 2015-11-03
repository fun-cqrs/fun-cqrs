package lottery.api

import com.softwaremill.macwire._
import lottery.domain.service.ServiceModule

trait ApiModule extends ServiceModule {

  val cmdController = wire[LotteryCmdController]
  val queryController = wire[LotteryQueryController]

}
