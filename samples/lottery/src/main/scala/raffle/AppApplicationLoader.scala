package raffle

import _root_.controllers.Assets
import com.softwaremill.macwire.MacwireMacros._
import raffle.api.{ApiModule, AkkaModule}
import raffle.app.RestHttpErrorHandler
import raffle.domain.service.{ServiceModule, RaffleModule}
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


