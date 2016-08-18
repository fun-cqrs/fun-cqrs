package io.funcqrs.akka

import akka.actor.ActorRef
import com.typesafe.config.{ ConfigFactory, Config }
import com.typesafe.scalalogging.LazyLogging
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

      // load the class using constructor with Config
      Thread.currentThread()
        .getContextClassLoader
        .loadClass(configuredClassName)
        .getDeclaredConstructor(classOf[Config])
        .newInstance(config)
        .asInstanceOf[PassivationStrategy]

    }.recoverWith {

      case e: NoSuchMethodException =>
        // try again using default constructor
        Try {
          Thread.currentThread()
            .getContextClassLoader
            .loadClass(configuredClassName)
            .getDeclaredConstructor()
            .newInstance()
            .asInstanceOf[PassivationStrategy]
        }

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
        //class not found, load default one
        logger.error("Unknown error while loading passivation strategy class. Falling back to default passivation strategy.", exp)
        new MaxChildrenPassivationStrategy(config)
    }.getOrElse(new MaxChildrenPassivationStrategy(config))
  }
}

/**
 * Common trait for Passivation Strategies that decides which actor to stop based on
 * the list of current active AggregateActors
 */
trait SelectionBasedPassivationStrategySupport extends PassivationStrategy {

  /**
   * Filter function to select actor children to terminate.
   *
   * @param candidates - iterable of current active actors
   * @return - a selection of Actors eligible for being terminated
   */
  def selectChildrenToKill(candidates: Iterable[ActorRef]): Iterable[ActorRef]

}

/**
 * Defines a passivation strategy that will kill child actors when creating a new child
 * will push us over a threshold
 */
class MaxChildrenPassivationStrategy(config: Config) extends SelectionBasedPassivationStrategySupport {

  val max = Try(config.getInt("max-children.max")).getOrElse(40)
  val killAtOnce = Try(config.getInt("max-children.kill-at-once")).getOrElse(20)

  def this() = this(ConfigFactory.load())

  def selectChildrenToKill(candidates: Iterable[ActorRef]): Iterable[ActorRef] = {
    if (candidates.size > max) {
      candidates.take(killAtOnce)
    } else {
      Nil
    }
  }

  override def toString = s"${this.getClass.getSimpleName}(max=$max, killAtOnce=$killAtOnce)"
}
