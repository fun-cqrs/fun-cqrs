package lottery.domain.service

import io.funcqrs.InMemoryRepository
import lottery.domain.model.{ LotteryId, LotteryView }

class LotteryViewRepo extends InMemoryRepository {

  type Identifier = LotteryId
  type Model = LotteryView

  /** Extract id van Model */
  protected def $id(model: LotteryView): LotteryId = model.id
}
