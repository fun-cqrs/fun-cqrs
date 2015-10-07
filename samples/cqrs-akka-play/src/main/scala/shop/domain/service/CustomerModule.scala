package shop.domain.service

import akka.actor.{ActorRef, Props}
import com.softwaremill.macwire._
import io.strongtyped.funcqrs.Behavior
import io.strongtyped.funcqrs.akka.{AggregateManager, AssignedAggregateId, ProjectionActor}
import shop.api.AkkaModule
import shop.app.LevelDbProjectionSource
import shop.domain.model.{Customer, CustomerId, CustomerView}

trait CustomerModule extends AkkaModule {

  val customerAggregateManager: ActorRef @@ Customer.type =
    actorSystem
      .actorOf(Props[CustomerAggregateManager], "CustomerAggregateManager")
      .taggedWith[Customer.type]

  //----------------------------------------------------------------------
  // READ side wiring
  val customerViewRepo = wire[CustomerViewRepo].taggedWith[CustomerView.type]

  funCQRS.projection(Props(classOf[CustomerViewProjectionActor], wire[CustomerViewProjection]), "CustomerViewProjectionActor")
}

class CustomerAggregateManager extends AggregateManager with AssignedAggregateId {

  type AggregateType = Customer

  def behavior(id: CustomerId): Behavior[Customer] = Customer.behavior(id)

}

class CustomerViewProjectionActor(val projection: CustomerViewProjection) extends ProjectionActor with LevelDbProjectionSource {

  val tag = Customer.tag
}