package raffle.api

import com.softwaremill.macwire._
import raffle.domain.service.ServiceModule

trait ApiModule extends ServiceModule {

  val productCmdController = wire[RaffleCmdController]
  val productQueryController = wire[RaffleQueryController]


}
