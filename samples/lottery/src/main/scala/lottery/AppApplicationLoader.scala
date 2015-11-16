package lottery

import _root_.controllers.Assets
import com.softwaremill.macwire.MacwireMacros._
import lottery.api.{ ApiModule, AkkaModule }
import lottery.app.RestHttpErrorHandler
import lottery.domain.service.{ ServiceModule, LotteryModule }
import play.api.ApplicationLoader.Context
import play.api._
import play.api.routing.Router
import router.Routes

import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps

class AppApplicationLoader extends ApplicationLoader {

  def load(context: Context) = {

    Logger.configure(context.environment)

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

