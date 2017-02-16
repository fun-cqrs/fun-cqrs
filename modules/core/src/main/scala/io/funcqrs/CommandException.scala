package io.funcqrs

import scala.util.control.NoStackTrace

class CommandException(msg: String) extends RuntimeException(msg) with NoStackTrace
class MissingCommandHandlerException(msg: String) extends RuntimeException(msg)
class MissingEventHandlerException(msg: String) extends RuntimeException(msg)
class MissingBehaviorException(msg: String) extends RuntimeException(msg)
class MissingAggregateConfiguration(msg: String) extends IllegalStateException(msg)
