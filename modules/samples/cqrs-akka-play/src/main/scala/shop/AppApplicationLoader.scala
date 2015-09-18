package shop

import _root_.controllers.Assets
import com.softwaremill.macwire.MacwireMacros._
import shop.api.{ApiModule, AkkaModule}
import shop.app.RestHttpErrorHandler
import shop.domain.service.{ServiceModule, ProductModule}
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

trait AppComponents extends BuiltInComponents with ApiModule with ServiceModule {

  lazy val assets: Assets = wire[Assets]
  lazy val router: Router = wire[Routes] withPrefix "/"

  override lazy val httpErrorHandler = new RestHttpErrorHandler
}


