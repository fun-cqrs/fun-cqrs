package fun.cqrs

import _root_.controllers.Assets
import com.softwaremill.macwire.MacwireMacros._
import fun.cqrs.shop.api.ControllerModule
import play.api.ApplicationLoader.Context
import play.api._
import play.api.routing.Router
import router.Routes
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps

class AppApplicationLoader extends ApplicationLoader {

  def load(context: Context) = {
    val app = new BuiltInComponentsFromContext(context) with AppComponents
    app.applicationLifecycle.addStopHook { () =>
      for {
        _ <- app.actorSystem.terminate()
        _ <- app.actorSystem.whenTerminated
      } yield ()
    }
    app.application
  }
}

trait AppComponents extends BuiltInComponents with ControllerModule {

  lazy val assets: Assets = wire[Assets]
  lazy val router: Router = wire[Routes] withPrefix "/"

}


