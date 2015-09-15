package fun.cqrs

import fun.cqrs.shop.api.ControllerModule
import play.api.ApplicationLoader.Context
import play.api._
import play.api.routing.Router
import _root_.controllers.Assets
import router.Routes
import com.softwaremill.macwire.MacwireMacros._

import scala.concurrent.ExecutionContext

class AppApplicationLoader extends ApplicationLoader {
  def load(context: Context) = {
    (new BuiltInComponentsFromContext(context) with AppComponents).application
  }
}

trait AppComponents extends BuiltInComponents with ControllerModule {

  // Application controllers {

  lazy val assets: Assets = wire[Assets]
  lazy val router: Router = wire[Routes] withPrefix "/"

}


