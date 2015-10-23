package raffle.domain.service

import io.strongtyped.funcqrs.InMemoryRepository
import raffle.domain.model.{RaffleId, RaffleView}

class RaffleViewRepo extends InMemoryRepository {

  type Identifier = RaffleId
  type Model = RaffleView

  /** Extract id van Model */
  protected def $id(model: RaffleView): RaffleId = model.id
}
