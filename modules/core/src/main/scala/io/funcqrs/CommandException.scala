package io.funcqrs

class CommandException(msg: String) extends RuntimeException(msg)
class MissingEventHandlerException(msg: String) extends RuntimeException(msg)
class MissingBehaviorException(msg: String) extends RuntimeException(msg)
