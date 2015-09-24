package fun.cqrs

// tag::adoc[]
object ProtocolDef {

  trait Commands {
    trait ProtocolCommand extends DomainCommand
  }

  trait Events {
    trait ProtocolEvent extends DomainEvent
  }

  trait Protocol extends Commands with Events

}

// end::adoc[]