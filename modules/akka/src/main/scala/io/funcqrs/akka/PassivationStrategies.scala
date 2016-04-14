package io.funcqrs.akka

import akka.actor.ActorRef
import com.typesafe.config.{ ConfigFactory, Config }
import com.typesafe.scalalogging.LazyLogging
import scala.concurrent.duration._
import scala.util.Try
import scala.util.control.NonFatal

/**
 * Defines a passivation strategy for aggregate instances.
 */
sealed trait PassivationStrategy

object PassivationStrategy extends LazyLogging {
  val configPathPrefix = "funcqrs.akka.aggregates"
  val configPathSuffix = "passivation-strategy"

  def apply(aggregateName: String) = {
    val _config = ConfigFactory.load()

    val aggregateConfigPath = s"$configPathPrefix.${aggregateName.toLowerCase}.$configPathSuffix"

    val configPath = if (_config.hasPath(aggregateConfigPath)) aggregateConfigPath else s"$configPathPrefix.$configPathSuffix"
    val config: Config = _config.getConfig(configPath)

    val configuredClassName = config.getString("class")

    Try {
      //laad de class
      Thread.currentThread()
        .getContextClassLoader
        .loadClass(configuredClassName)
        .getDeclaredConstructor(classOf[Config])
        .newInstance(config)
        .asInstanceOf[PassivationStrategy]
    }.recover {
      case e: ClassNotFoundException =>

        logger.warn(
          s"""
               |#=============================================================================
               |# Could not load class configured for {}.class.
               |# Are you sure {} is correct and in your classpath?
               |#
               |# Falling back to default passivation strategy
               |#=============================================================================
          """.stripMargin, configPath, configuredClassName
        )

        new MaxChildrenPassivationStrategy(config)

      case _: InstantiationException | _: IllegalAccessException =>

        logger.warn(
          """"
              |#=======================================================================================
              |# Could not instantiate the passivation strategy.
              |# Are you sure {} has a constructor for Config and is a subclass of PassivationStrategy?
              |#
              |# Falling back to default passivation strategy
              |#====================================================================================
            """.stripMargin, configuredClassName
        )

        new MaxChildrenPassivationStrategy(config)

      case NonFatal(exp) =>
        //class niet gevonden, laad de default
        logger.error("Unknown error while loading passivation strategy class. Falling back to default passivation strategy.", exp)
        new MaxChildrenPassivationStrategy(config)
    }.getOrElse(new MaxChildrenPassivationStrategy(config))
  }
}

/**
 * Defines a passivation strategy that will kill child actors when creating a new child
 * will push us over a threshold
 */
trait SelectionBasedPassivationStrategySupport extends PassivationStrategy {

  def selectChildrenToKill(candidates: Iterable[ActorRef]): Iterable[ActorRef]

}

class MaxChildrenPassivationStrategy(config: Config) extends SelectionBasedPassivationStrategySupport {

  val max = Try(config.getInt("max-children.max")).getOrElse(40)
  val killAtOnce = Try(config.getInt("max-children.kill-at-once")).getOrElse(20)

  def selectChildrenToKill(candidates: Iterable[ActorRef]): Iterable[ActorRef] = {
    if (candidates.size > max) {
      candidates.take(killAtOnce)
    } else {
      Nil
    }
  }

  override def toString = s"${this.getClass.getSimpleName}(max=$max,killAtOnce=$killAtOnce)"
}

trait InactivityTimeoutPassivationStrategySupport extends PassivationStrategy {

  /**
   * Determines how long they can idle in memory
   *
   * @return
   */
  def inactivityTimeout: Duration

  override def toString = s"${this.getClass.getSimpleName}(inactivityTimeout=$inactivityTimeout)"
}

/**
 * Defines a passivation strategy that will kill child actors when they have been idle for too long
 */
class InactivityTimeoutPassivationStrategy(config: Config) extends InactivityTimeoutPassivationStrategySupport {
  override val inactivityTimeout: Duration = {
    Try(Duration(config.getLong("inactivity-timeout"), SECONDS)).getOrElse(1.hours)
  }

}