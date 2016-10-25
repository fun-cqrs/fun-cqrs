package lottery.domain.service

import lottery.domain.model.{ LotteryId, LotteryView }

class LotteryViewRepo extends InMemoryRepository {

  type Identifier = LotteryId
  type Model      = LotteryView

  /** Extract id from Model */
  protected def $id(view: LotteryView): LotteryId = view.id
}
