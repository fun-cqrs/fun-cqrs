package io.funcqrs.akka.util
import org.scalatest.flatspec.AsyncFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class ConfigReaderTest extends AsyncFlatSpecLike with Matchers {

  behavior of "ConfigReader"

  it should "return the specific value if it's present" in {
    val reader = ConfigReader.aggregateConfig("aggregate-name")

    val duration = reader.getDuration("ask-timeout", 10.seconds)
    duration shouldBe 15.seconds

    val int = reader.getInt("events-per-snapshot", 10)
    int shouldBe 200
  }

  it should "read global properties if can't find specific value" in {
    val reader = ConfigReader.aggregateConfig("bogus-aggregate-name")

    val duration = reader.getDuration("ask-timeout", 10.seconds)
    duration shouldBe 5.seconds

    val int = reader.getInt("events-per-snapshot", 10)
    int shouldBe 100
  }

  it should "return default value if it can't find a key" in {
    // paths are correct, but key not
    val reader = ConfigReader.aggregateConfig("bogus-aggregate-name")

    val duration = reader.getDuration("foo", 10.seconds)
    duration shouldBe 10.seconds

    val int = reader.getInt("foo", 10)
    int shouldBe 10
  }

  it should "return default value if it can't find specific or global values" in {
    // paths and key are not correct
    val reader = ConfigReader.readerFor("non-existent.specific", "non-existent")

    val duration = reader.getDuration("foo", 10.seconds)
    duration shouldBe 10.seconds

    val int = reader.getInt("foo", 10)
    int shouldBe 10
  }
}
