package fun.cqrs

trait Logging {

  case class Logger(name: String) {
    def info(msg: String) = println(s"INFO: $name - $msg")
    def debug(msg: String) = println(s"DEBUG: $name - $msg")
  }

  val logger = {
    val cls = getClass.getName
    Logger("application." + cls)
  }
}

trait LoggingSuffix {
  this: Logging =>

  def suffix: String
  override val logger = {
    val cls = getClass.getName
    Logger("application." + cls + s".$suffix")
  }
}