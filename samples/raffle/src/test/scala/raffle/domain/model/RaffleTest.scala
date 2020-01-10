package raffle.domain.model

import io.funcqrs.config.Api._
import io.funcqrs.test.InMemoryTestSupport
import io.funcqrs.test.backend.InMemoryBackend
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.{OptionValues, TryValues}
import raffle.domain.service.{RaffleViewProjection, RaffleViewRepo}

class RaffleTest extends AnyFunSuite with Matchers with OptionValues with TryValues {

  val repo = new RaffleViewRepo

  val id = RaffleId("test-raffle")

  class RaffleInMemoryTest extends InMemoryTestSupport {

    def configure(backend: InMemoryBackend): Unit = {
      // ---------------------------------------------
      // aggregate config - write model
      backend.configure {
        aggregate(Raffle.behavior)
      }

      // ---------------------------------------------
      // projection config - read model
      backend.configure {
        projection(
          projection       = new RaffleViewProjection(repo),
          publisherFactory = backend.inMemoryPublisherFactory[RaffleEvent],
          name             = "RaffleViewProjection"
        )
      }
    }

    def raffleRef(id: RaffleId) =
      backend.aggregateRef[Raffle].forId(id)

  }

  test("Run a Raffle") {

    new RaffleInMemoryTest {

      val raffle = raffleRef(id)

      // send all commands
      raffle ? CreateRaffle
      raffle ? AddParticipant("John")
      raffle ? AddParticipant("Paul")
      raffle ? Run

      // assert that expected events were produced
      expectEvent[RaffleCreated]
      expectEventPF { case ParticipantAdded("John", _) => () }
      expectEventPF { case ParticipantAdded("Paul", _) => () }
      expectEvent[WinnerSelected]

      // check the view projection
      val view = repo.find(id).success.value
      view.participants should have size 2
      view.winner shouldBe defined
    }

  }

  test("Run a raffle twice") {

    new RaffleInMemoryTest {

      val raffle = raffleRef(id)

      raffle ? CreateRaffle
      raffle ? AddParticipant("John")
      raffle ? AddParticipant("Paul")
      raffle ? Run

      intercept[RaffleHasAlreadyAWinner] {
        raffle ? Run
      }
    }

  }

  test("Run a raffle without participants") {

    new RaffleInMemoryTest {

      val raffle = raffleRef(id)

      raffle ? CreateRaffle

      intercept[IllegalArgumentException] {
        raffle ? Run
      }.getMessage shouldBe "Raffle has no participants"
    }

  }

  test("Add twice the same participant") {

    new RaffleInMemoryTest {

      val raffle = raffleRef(id)

      raffle ? CreateRaffle
      raffle ? AddParticipant("John")

      intercept[IllegalArgumentException] {
        raffle ? AddParticipant("John")
      }.getMessage shouldBe "Participant John already added!"
    }

  }

  test("Reset raffle") {

    new RaffleInMemoryTest {

      val raffle = raffleRef(id)

      raffle ? CreateRaffle
      raffle ? AddParticipant("John")
      raffle ? AddParticipant("Paul")

      val view = repo.find(id).success.value
      view.participants should have size 2

      raffle ? RemoveAllParticipants

      val updatedView = repo.find(id).success.value
      updatedView.participants should have size 0

    }

  }

  test("Illegal to Reset a raffle that has a winner already") {

    new RaffleInMemoryTest {

      val raffle = raffleRef(id)

      raffle ? CreateRaffle
      raffle ? AddParticipant("John")
      raffle ? AddParticipant("Paul")
      raffle ? Run

      val view = repo.find(id).success.value
      view.participants should have size 2
      view.winner shouldBe defined

      intercept[RaffleHasAlreadyAWinner] {
        // resetting is illegal if a winner is selected
        raffle ? RemoveAllParticipants
      }

      val updateView = repo.find(id).success.value
      updateView.participants should have size 2

    }

  }

  test("Illegal to add new participants to a raffle that has a winner already") {

    new RaffleInMemoryTest {

      val raffle = raffleRef(id)

      raffle ? CreateRaffle
      raffle ? AddParticipant("John")
      raffle ? AddParticipant("Paul")
      raffle ? Run

      val view = repo.find(id).success.value
      view.participants should have size 2
      view.winner shouldBe defined

      intercept[RaffleHasAlreadyAWinner] {
        // adding new participant is illegal if a winner is selected
        raffle ? AddParticipant("Ringo")
      }
    }

  }
}
