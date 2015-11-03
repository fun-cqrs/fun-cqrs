import java.util.UUID

trait DomainCommand
trait DomainEvent

trait ProtocolDef {
  trait ProtocolCommand extends DomainCommand
  trait ProtocolEvent extends DomainEvent
}


trait Aggregate {
  type Protocol <: ProtocolDef
}

object ProductProtocol extends ProtocolDef {
  case class CreateProduct(name: String, price: Double) extends ProtocolCommand
  case class ProductCreated(name: String, price: Double, id: String) extends ProtocolEvent
}


case class Product(name: String, price: Double) extends Aggregate {
  type Protocol = ProductProtocol.type
}


trait Behavior[A <: Aggregate] {

  type AggregateType = A
  type Command = AggregateType#Protocol#ProtocolCommand
  type Event = AggregateType#Protocol#ProtocolEvent


  def validate(cmd: Command): Event
  def applyEvent(evt: Event) : AggregateType
}


import ProductProtocol._

val behavior = new Behavior[Product] {

  def validate(cmd: Command) = {
    cmd match {
      case c: CreateProduct => ProductCreated(c.name, c.price, UUID.randomUUID.toString)
    }
  }


  def applyEvent(evt: Event) : Product = {
    evt match {
      case e : ProductCreated => Product(e.name, e.price)
    }
  }
}

// def call() = behavior.validate(new DomainCommand {})