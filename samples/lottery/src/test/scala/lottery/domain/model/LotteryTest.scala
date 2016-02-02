package lottery.domain.model

import io.funcqrs._
import lottery.domain.model.LotteryProtocol.{ AddParticipant, CreateLottery, RemoveAllParticipants, Run }
import lottery.domain.service.{ LotteryViewProjection, LotteryViewRepo }
import org.scalatest.concurrent.{ Futures, ScalaFutures }
import org.scalatest.time.{ Seconds, Span }
import org.scalatest.{ FunSuite, Matchers, OptionValues }

import scala.concurrent.ExecutionContext.Implicits.global

class LotteryTest extends FunSuite with Matchers with Futures
    with ScalaFutures with FunCqrsTestSupport with OptionValues with FailedFutures {

  override implicit val patienceConfig = PatienceConfig(timeout = Span(5, Seconds))

  val id = LotteryId("test-lottery")
  val lotteryBehavior = Lottery.behavior(LotteryId("test-lottery"))

  val repo = new LotteryViewRepo
  val projection = new LotteryViewProjection(repo)

  test("Run a Lottery") {

    new End2EndTestSupport(projection) {
      val lottery =
        lotteryBehavior
          .newInstance(CreateLottery("TestLottery"))
          .update(AddParticipant("John"))
          .update(AddParticipant("Paul"))
          .update(Run)

      lottery.aggregate.hasWinner shouldBe true
      lottery.aggregate.participants should have size 2

      val view = repo.find(lottery.aggregate.id).futureValue
      view.participants should have size 2
      view.winner shouldBe defined
    }

  }

  test("Run a Lottery twice") {

    new End2EndTestSupport(projection) {

      intercept[IllegalArgumentException] {

        lotteryBehavior
          .newInstance(CreateLottery("TestLottery"))
          .update(AddParticipant("John"))
          .update(AddParticipant("Paul"))
          .update(Run)
          .update(Run)

      }.getMessage shouldBe "Lottery has already a winner!"
    }
  }

  test("Run a Lottery without participants") {

    new End2EndTestSupport(projection) {

      intercept[IllegalArgumentException] {

        lotteryBehavior
          .newInstance(CreateLottery("TestLottery"))
          .update(Run)

      }.getMessage shouldBe "Lottery has no participants"
    }

  }

  test("Add twice the same participant") {

    new End2EndTestSupport(projection) {

      intercept[IllegalArgumentException] {

        lotteryBehavior
          .newInstance(CreateLottery("TestLottery"))
          .update(AddParticipant("John"))
          .update(AddParticipant("John"))

      }.getMessage shouldBe "Participant John already added!"

    }
  }

  test("Reset lottery") {

    new End2EndTestSupport(projection) {

      val lottery =
        lotteryBehavior
          .newInstance(CreateLottery("TestLottery"))
          .update(AddParticipant("John"))
          .update(AddParticipant("Paul"))

      val view = repo.find(lottery.aggregate.id).futureValue
      view.participants should have size 2

      lottery.update(RemoveAllParticipants)

      val updateView = repo.find(lottery.aggregate.id).futureValue
      updateView.participants should have size 0

    }
  }

  test("Illegal to Reset a lottery that has a winner already") {

    new End2EndTestSupport(projection) {

      val lottery =
        lotteryBehavior
          .newInstance(CreateLottery("TestLottery"))
          .update(AddParticipant("John"))
          .update(AddParticipant("Paul"))
          .update(Run)

      val view = repo.find(lottery.aggregate.id).futureValue
      view.participants should have size 2

      intercept[IllegalArgumentException] {

        lottery.update(RemoveAllParticipants) // reseting is illegal if a winner is selected

      }.getMessage shouldBe "Lottery has already a winner!"

      val updateView = repo.find(lottery.aggregate.id).futureValue
      updateView.participants should have size 2

    }
  }

  test("Illegal to add new participants to a lottery that has a winner already") {

    new End2EndTestSupport(projection) {

      val lottery =
        lotteryBehavior
          .newInstance(CreateLottery("TestLottery"))
          .update(AddParticipant("John"))
          .update(AddParticipant("Paul"))
          .update(Run)

      val view = repo.find(lottery.aggregate.id).futureValue
      view.participants should have size 2

      intercept[IllegalArgumentException] {

        lottery.update(AddParticipant("Ringo")) // reseting is illegal if a winner is selected

      }.getMessage shouldBe "Lottery has already a winner!"

      val updateView = repo.find(lottery.aggregate.id).futureValue
      updateView.participants should have size 2

    }
  }
}
