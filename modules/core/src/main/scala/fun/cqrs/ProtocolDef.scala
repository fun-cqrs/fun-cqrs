package fun.cqrs
// tag::adoc[]
object ProtocolDef {

  trait Create
  trait Update

  trait Commands {
    trait CreateCmd extends DomainCommand with Create
    trait UpdateCmd extends DomainCommand with Update
  }

  trait Events {
    trait CreateEvent extends DomainEvent with Create
    trait UpdateEvent extends DomainEvent with Update
  }

  trait Protocol extends Commands with Events
}
// end::adoc[]