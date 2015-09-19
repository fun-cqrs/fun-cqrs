package shop.app
import play.api.Logger

trait Logging {

  val logger = {    
    val cls = getClass.getName
    Logger("application." + cls)
  }
}

trait LoggingSuffix { this:Logging =>
  
  def suffix : String
  override val logger = {    
    val cls = getClass.getName
    Logger("application." + cls + ".suffix")
  }
}