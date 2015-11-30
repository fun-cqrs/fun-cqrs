package shop.domain.service

import com.softwaremill.macwire._
import io.funcqrs.akka.FunCQRS.api._
import shop.api.AkkaModule
import shop.app.LevelDbTaggedEventsSource
import shop.domain.model.{ Customer, CustomerView }

trait CustomerModule extends AkkaModule {

  val customerService =
    config {
      aggregate[Customer](Customer.behavior)
        .withAssignedId
    }

  //----------------------------------------------------------------------
  // READ side wiring
  val customerViewRepo = wire[CustomerViewRepo].taggedWith[CustomerView.type]

  config {
    projection(
      sourceProvider = new LevelDbTaggedEventsSource(Customer.tag),
      projection = wire[CustomerViewProjection],
      name = "CustomerViewProjectionActor"
    ).withoutOffsetPersistence
  }

}
