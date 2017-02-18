package io.funcqrs

import scala.util.control.NoStackTrace

class InvalidCommandException(msg: String) extends IllegalArgumentException(msg) with NoStackTrace

class CommandException(msg: String) extends IllegalArgumentException(msg)

class MissingCommandHandlerException(msg: String) extends IllegalArgumentException(msg)

class MissingEventHandlerException(msg: String) extends IllegalStateException(msg)

class MissingBehaviorException(msg: String) extends IllegalStateException(msg)

class MissingAggregateConfigurationException(msg: String) extends IllegalStateException(msg)
