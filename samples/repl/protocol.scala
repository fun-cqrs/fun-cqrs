import java.util.UUID

trait DomainCommand
trait DomainEvent
trait Aggregate


case class CreateProduct(name: String, price: Double) extends DomainCommand
case class ProductCreated(name: String, price: Double, id: String) extends DomainEvent


case class Product(name: String, price: Double) extends Aggregate


trait Behavior[A <: Aggregate] {
  def validate(cmd: DomainCommand): DomainEvent
  def applyEvent(evt: DomainEvent) : A
}


val behavior = new Behavior[Product] {

  def validate(cmd: DomainCommand): DomainEvent = {
    cmd match {
      case c: CreateProduct => ProductCreated(c.name, c.price, UUID.randomUUID.toString)
    }
  }

  def applyEvent(evt: DomainEvent) : Product = {
    evt match {
      case e : ProductCreated => Product(e.name, e.price)
    }
  }
}

def call() = behavior.validate(new DomainCommand {})

//call()