package io.funcqrs.akka.util

import scala.util.Try

trait ConfigReader {

  /**
   * Try to get a configuration value in a specific order.
   * First the specific location, then the global and if all fails the hardcoded default value
   *
   * For example:
   * {{{
   * getConfig(
   *   context.system.settings.config.getInt(s"funcqrs.akka.aggregates.$aggregateType.events-per-snapshot"),
   *   context.system.settings.config.getInt("funcqrs.akka.aggregates.events-per-snapshot"),
   *   defaultValue = 200
   * )
   * }}}
   */
  def getConfig[T](specific: => T, global: => T, defaultValue: => T) = {

    val value = Try {
      specific
    } recoverWith {
      case _ => Try(global)
    } getOrElse {
      defaultValue
    }
    value
  }
}

object ConfigReader extends ConfigReader {
}
