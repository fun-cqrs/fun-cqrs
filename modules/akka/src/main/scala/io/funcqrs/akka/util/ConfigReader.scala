package io.funcqrs.akka.util

import java.util.concurrent.TimeUnit

import com.typesafe.config.{ Config, ConfigFactory }

import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

case class ConfigReader(specificPath: String, globalPath: String, config: Config) {

  lazy val specificConfig = Try(config.getConfig(specificPath))
  lazy val globalConfig   = Try(config.getConfig(globalPath))

  /**
    * Get a duration value.
    *
    * @param propertyName - property name to lookup
    * @param default - default [[Duration]] value
    * @return return value of `propertyName` or `default` if property is not found at `specificPath` and at `globalPath`
    */
  def getDuration(propertyName: String, default: => FiniteDuration): FiniteDuration =
    readConfig(
      _.getDuration(propertyName, TimeUnit.MILLISECONDS).millis,
      default
    )

  /**
    * Get an integer value.
    *
    * @param propertyName - property name to lookup
    * @param default - default [[Int]] value
    * @return return value of `propertyName` or `default` if property is not found at `specificPath` and at `globalPath`
    */
  def getInt(propertyName: String, default: => Int): Int =
    readConfig(
      _.getInt(propertyName),
      default
    )

  /**
    * Try to get a configuration value in a specific order.
    * First the specific location, then the global and if all fails the hardcoded default value
    */
  private def readConfig[T](readConfigFunc: Config => T, defaultValue: => T): T = {

    // read from specific node
    specificConfig.map { config =>
      readConfigFunc(config)
    } recoverWith {
      // fallback to global node
      case _ =>
        globalConfig.map { config =>
          readConfigFunc(config)
        }
    } getOrElse {
      // finally return default value if nothing works
      defaultValue
    }

  }
}
object ConfigReader {
  private lazy val config = ConfigFactory.load()

  private val aggregatePathPrefix  = "funcqrs.akka.aggregates"
  private val projectionPathPrefix = "funcqrs.akka.projections"

  def aggregateConfig[T](aggregateName: String): ConfigReader = {
    readerFor(aggregatePathPrefix + "." + aggregateName, aggregatePathPrefix)
  }

  def projectionConfig[T](aggregateName: String): ConfigReader =
    readerFor(projectionPathPrefix + "." + aggregateName, projectionPathPrefix)

  def readerFor(specific: String, global: String): ConfigReader =
    readerFor(specific, global, config)

  def readerFor(specific: String, global: String, config: Config): ConfigReader =
    ConfigReader(specific, global, config)
}
