# Roadmap to 1.0.0

Fun.CQRS is undergoing a considerable refactoring that will culminate with the release of version 1.0.0. 

This document describe what are the main changes in the API  and describe how we will phase it.

Those changes are backward incompatible. On it's core, Fun.CQRS stays the same, but on API level we need to make some adjustments which we will explain in detail. A migration guide will be publish for each phase.

We will break it up into three milestones so users can choose to gradually migrate their code base on each milestone release or to choose for a big bang migration. 

* The first milestone affects the protocol definition and the `Behavior` API only. 
* Milestone 2 will impact the `Projection` API. 
* Finally milestone 3 will add support for clustering to the Akka Backend. 

Note, only M1 is expected to have a considerable impact. Some mechanical migration will be needed for M2, but ti will be much less intrusive. Akka cluster support should also be a low-impact migration. 

# Milestone 1 (1.0.0-M1)

This first milestone includes the refactorings related to [#75](https://github.com/strongtyped/fun-cqrs/issues/75) and [#76](https://github.com/strongtyped/fun-cqrs/issues/76).

## Issue [#75](https://github.com/strongtyped/fun-cqrs/issues/75)

The main motivation for this was the need to replace type projections by path dependent types. This is explained on the ticket itself [#75](https://github.com/strongtyped/fun-cqrs/issues/75) and on [this talk](https://skillsmatter.com/skillscasts/9112-method-reification-and-type-safety-in-a-cqrs-world) at Scala Exchange 2016.

Moving to path dependent types makes the `Behavior` API much more flexible and less verbose. It's no longer needed to extends `ProtocolLike` trait nor `ProtocolCommand` and `ProtocolEvent` sub-traits.

This has the extra advantage of opening the doors for using serialization libraries like protobuf to generate `Command` and `Event` case classes. 

## Issue [#76](https://github.com/strongtyped/fun-cqrs/issues/76)

The `Actions` API imposed the usage of total function for the declaration of `Command Handlers` and `Event Handlers`.

The main reason for that was that we want to have an API for declaring `Command Handlers` that had the same shape disregarding the return type of the `Command Handlers`.

It was possible to write the following:

```scala
actions
  .handleCommand {
    cmd: SomeCommand => SomeEvent(...)
  }
  .handleCommand {
    cmd: AnotherCommand => Future.successful(SomeOtherEvent(...))
  }
```

Note that the first `Command Handler` returns an unboxed type, while the second has it wrapped in a `Future`.

In order to do it we needed to make use of `ClassTag`s and implicit resolution of `CommandInvokers`. 

At first, this seemed a good idea, but it introduced some complications and unexpected and cryptic compilation errors.

Developers new to the library got puzzled by the fact that it was not possible to pass a `PartialFunction`. Scala developers know that whenever a `Total Function` is required, a `PartialFunction` can be passed. Which is kind of odd, but that is how Scala works. Even more confusing, is that although `Fun.CQRS` were requiring `Total Functions`, it was building `PartialFunction` out of it based on the input type of the function. Hence the need for `ClassTag`.

For instance, this wasn't possible:

```scala
actions
  .handleCommand {
    case SomeCommand(foo) => SomeEvent(foo)
  }
```
The code above won't compile because `handleCommand` required a `ClassTag` and the compiler can't provide one based on the input parameter of a `PartialFunction`.

Because of this design, it was very hard to build extension points to support new types. Our experiments building a `Cats` backend was unsatisfactory. Extension libraries wouldn't be able offer the same coding experience when defining `Command Handlers`.

Therefore we decided to move out of implicit `CommandInvokers` and `ClassTag`.

The new API is a little bit more verbose, but it is explicit on its intention. From a developer perspective, it's also clear what can be done and how to get it right. For instance, the first `CommandHandler` example on the new API have to be written as following:

```scala
actions
  .commandHandler {
    OneEvent {
      cmd: SomeCommand => SomeEvent(...)
    }
  }
  .handleCommand {
    eventually.OneEvent {
		cmd: AnotherCommand => 
			Future.successful(SomeOtherEvent(...))
    }
  }
```

Note that instead of requiring implicit `CommandInvoker` in scope, we must now explicit declare them using `OneEvent` and `eventually.OneEvent`.

# Milestone 2 (1.0.0-M2)

Will include a revision of the `Projection` API. 

The current `Projection` design have a couple of issues:  

## Alternative Stream Sources

While it's a totally valid use case to consume `Events` from another bounded context / application, in it's current incarnation `Projections` in **Fun.CQRS** can only consume `Events` from the current backend.

We should be able to consume `Events` from a Kafka topic, from a RSS feed or from a Redis queue. 


## Offset persistence and idem-potency

In it's current form, offsets are always persisted from outside the `Projection`. Forcing `Projections` to be idem-potent. 

Although idem-potency is a must for any process with at-least-once-delivery semantics, in some situations this could be avoided. For instance, `Projections` pushing data to a DB using `Slick` could have the offset persisted on the same transaction as the view.

In order to achieve this we must be able to pass the current offset along with the `Event` being consumed. 

Moreover, **Fun.CQRS** currently defines the offset as a long, but that it's not necessarily the case for all kind of projections. An offset can be a timestamp based UUID (cassandra akka journal) or even a String (RSS feed source).

# Milestone 3 (1.0.0-Final)

The final milestone will bring support to akka cluster for the `AkkaBackend`. This is documented on [this GitHub issue](https://github.com/strongtyped/fun-cqrs/issues/62).

The design is not yet finished, but basically we have two options. 1) adapt the current `AkkaBackend` to support clustering; 2) develop new backend: `AkkaBackendClustered`. 

We will only choose for option 2 if option 1 reveals to be too intrusive. There are a couple of projects using `Fun.CQRS` that are not being deployed in a cluster. Those projects should not be impacted by this, unless it's a very minimal adaptation.