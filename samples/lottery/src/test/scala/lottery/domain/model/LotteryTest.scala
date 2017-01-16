package lottery.domain.model

import io.funcqrs.backend.QuerySelectAll
import io.funcqrs.config.Api._
import io.funcqrs.test.InMemoryTestSupport
import io.funcqrs.test.backend.InMemoryBackend
import lottery.domain.service.{ LotteryViewProjection, LotteryViewRepo }
import org.scalatest.{ FunSuite, Matchers, OptionValues, TryValues }

class LotteryTest extends FunSuite with Matchers with OptionValues with TryValues {

  val repo = new LotteryViewRepo

  val id = LotteryId("test-lottery")

  class LotteryInMemoryTest extends InMemoryTestSupport[LotteryEvent] {

    def configure(backend: InMemoryBackend): Unit = {
      // ---------------------------------------------
      // aggregate config - write model
      backend.configure {
        aggregate(Lottery.behavior)
      }

      // ---------------------------------------------
      // projection config - read model
      backend.configure {
        projection(
          // we don't use tagging for in-memory tests
          query      = QuerySelectAll,
          projection = new LotteryViewProjection(repo),
          name       = "LotteryViewProjection"
        )
      }
    }

    def lotteryRef(id: LotteryId) =
      backend.aggregateRef[Lottery].forId(id)

  }

  test("Run a Lottery") {

    new LotteryInMemoryTest {

      val lottery = lotteryRef(id)

      // send all commands
      lottery ? CreateLottery
      lottery ? AddParticipant("John")
      lottery ? AddParticipant("Paul")
      lottery ? Run

      // assert that expected events were produced
      expectEvent[LotteryCreated]
      expectEventPF { case ParticipantAdded("John", _) => () }
      expectEventPF { case ParticipantAdded("Paul", _) => () }
      expectEvent[WinnerSelected]

      // check the view projection
      val view = repo.find(id).success.value
      view.participants should have size 2
      view.winner shouldBe defined
    }

  }

  test("Run a Lottery twice") {

    new LotteryInMemoryTest {

      val lottery = lotteryRef(id)

      lottery ? CreateLottery
      lottery ? AddParticipant("John")
      lottery ? AddParticipant("Paul")
      lottery ? Run

      intercept[LotteryHasAlreadyAWinner] {
        lottery ? Run
      }
    }

  }

  test("Run a Lottery without participants") {

    new LotteryInMemoryTest {

      val lottery = lotteryRef(id)

      lottery ? CreateLottery

      intercept[IllegalArgumentException] {
        lottery ? Run
      }.getMessage shouldBe "Lottery has no participants"
    }

  }

  test("Add twice the same participant") {

    new LotteryInMemoryTest {

      val lottery = lotteryRef(id)

      lottery ? CreateLottery
      lottery ? AddParticipant("John")

      intercept[IllegalArgumentException] {
        lottery ? AddParticipant("John")
      }.getMessage shouldBe "Participant John already added!"
    }

  }

  test("Reset lottery") {

    new LotteryInMemoryTest {

      val lottery = lotteryRef(id)

      lottery ? CreateLottery
      lottery ? AddParticipant("John")
      lottery ? AddParticipant("Paul")

      val view = repo.find(id).success.value
      view.participants should have size 2

      lottery ? RemoveAllParticipants

      val updatedView = repo.find(id).success.value
      updatedView.participants should have size 0

    }

  }

  test("Illegal to Reset a lottery that has a winner already") {

    new LotteryInMemoryTest {

      val lottery = lotteryRef(id)

      lottery ? CreateLottery
      lottery ? AddParticipant("John")
      lottery ? AddParticipant("Paul")
      lottery ? Run

      val view = repo.find(id).success.value
      view.participants should have size 2
      view.winner shouldBe defined

      intercept[LotteryHasAlreadyAWinner] {
        // resetting is illegal if a winner is selected
        lottery ? RemoveAllParticipants
      }

      val updateView = repo.find(id).success.value
      updateView.participants should have size 2

    }

  }

  test("Illegal to add new participants to a lottery that has a winner already") {

    new LotteryInMemoryTest {

      val lottery = lotteryRef(id)

      lottery ? CreateLottery
      lottery ? AddParticipant("John")
      lottery ? AddParticipant("Paul")
      lottery ? Run

      val view = repo.find(id).success.value
      view.participants should have size 2
      view.winner shouldBe defined

      intercept[LotteryHasAlreadyAWinner] {
        // adding new participant is illegal if a winner is selected
        lottery ? AddParticipant("Ringo")
      }
    }

  }
}
