package lottery.domain.model

import io.funcqrs.test.InMemoryTestSupport
import io.funcqrs.test.backend.InMemoryBackend
import lottery.app.LotteryBackendConfig
import lottery.domain.model.LotteryProtocol._
import lottery.domain.service.LotteryViewRepo
import org.scalatest.concurrent.{ Futures, ScalaFutures }
import org.scalatest.{ FunSuite, Matchers, OptionValues }

import scala.concurrent.ExecutionContext.Implicits.global

class LotteryTest extends FunSuite with Matchers with Futures
    with OptionValues with ScalaFutures with FailedFutures {

  val repo = new LotteryViewRepo

  val id = LotteryId("test-lottery")

  class LotteryInMemoryTest extends InMemoryTestSupport {

    def configure(backend: InMemoryBackend): Unit = {
      LotteryBackendConfig(backend, repo)
    }

    def lotteryRef(id: LotteryId) = aggregateRef[Lottery](id)
  }

  test("Run a Lottery") {

    new LotteryInMemoryTest {

      val lottery = lotteryRef(id)

      // send all commands
      lottery ? CreateLottery("TestLottery")
      lottery ? AddParticipant("John")
      lottery ? AddParticipant("Paul")
      lottery ? Run

      // assert that expected events were produced
      expectEventType[LotteryCreated]
      expectEvent { case ParticipantAdded("John", _) => () }
      expectEvent { case ParticipantAdded("Paul", _) => () }
      expectEventType[WinnerSelected]

      // check the view projection
      val view = repo.find(id).futureValue
      view.participants should have size 2
      view.winner shouldBe defined
    }

  }

  test("Run a Lottery twice") {

    new LotteryInMemoryTest {

      val lottery = lotteryRef(id)

      lottery ? CreateLottery("TestLottery")
      lottery ? AddParticipant("John")
      lottery ? AddParticipant("Paul")
      lottery ? Run

      intercept[IllegalArgumentException] {
        lottery ? Run
      }.getMessage shouldBe "Lottery has already a winner!"
    }

  }

  test("Run a Lottery without participants") {

    new LotteryInMemoryTest {

      val lottery = lotteryRef(id)

      lottery ? CreateLottery("TestLottery")

      intercept[IllegalArgumentException] {
        lottery ? Run
      }.getMessage shouldBe "Lottery has no participants"
    }

  }

  test("Add twice the same participant") {

    new LotteryInMemoryTest {

      val lottery = lotteryRef(id)

      lottery ? CreateLottery("TestLottery")
      lottery ? AddParticipant("John")

      intercept[IllegalArgumentException] {
        lottery ? AddParticipant("John")
      }.getMessage shouldBe "Participant John already added!"
    }

  }

  test("Reset lottery") {

    new LotteryInMemoryTest {

      val lottery = lotteryRef(id)

      lottery ? CreateLottery("TestLottery")
      lottery ? AddParticipant("John")
      lottery ? AddParticipant("Paul")

      val view = repo.find(id).futureValue
      view.participants should have size 2

      lottery ? RemoveAllParticipants

      val updatedView = repo.find(id).futureValue
      updatedView.participants should have size 0

    }

  }

  test("Illegal to Reset a lottery that has a winner already") {

    new LotteryInMemoryTest {

      val lottery = lotteryRef(id)

      lottery ? CreateLottery("TestLottery")
      lottery ? AddParticipant("John")
      lottery ? AddParticipant("Paul")
      lottery ? Run

      val view = repo.find(id).futureValue
      view.participants should have size 2
      view.winner shouldBe defined

      intercept[IllegalArgumentException] {

        lottery ? RemoveAllParticipants // reseting is illegal if a winner is selected

      }.getMessage shouldBe "Lottery has already a winner!"

      val updateView = repo.find(id).futureValue
      updateView.participants should have size 2

    }

  }

  test("Illegal to add new participants to a lottery that has a winner already") {

    new LotteryInMemoryTest {

      val lottery = lotteryRef(id)

      lottery ? CreateLottery("TestLottery")
      lottery ? AddParticipant("John")
      lottery ? AddParticipant("Paul")
      lottery ? Run

      val view = repo.find(id).futureValue
      view.participants should have size 2
      view.winner shouldBe defined

      intercept[IllegalArgumentException] {

        lottery ? AddParticipant("Ringo") // adding new participant is illegal if a winner is selected

      }.getMessage shouldBe "Lottery has already a winner!"
    }

  }
}
