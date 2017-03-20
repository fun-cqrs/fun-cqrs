# Roadmap to 1.0.0 and 1.1.0

Fun.CQRS is undergoing a considerable refactoring that will culminate with the release of version 1.0.0. 

This document describe what are the main changes in the API  and describe how we will phase it.

Those changes are backward incompatible. On it's core, Fun.CQRS stays the same, but on API level we need to make some adjustments which we will explain in detail. A migration guide will be publish for each phase.

# Release 1.0.0

Before the final 1.0.0 release, we will have two milestone.   

The first milestone affects the protocol definition and the `Behavior` API only while milestone 2 will impact the `Projection` API.

# Milestone 1 (1.0.0-M1)

This first milestone includes the refactorings related to #75 and #76.

## Issue #75

The main motivation for this was the need to replace type projections by path dependent types. This is explained on the ticket itself (#75) and on [this talk](https://skillsmatter.com/skillscasts/9112-method-reification-and-type-safety-in-a-cqrs-world) at Scala Exchange.

Moving to path dependent types makes the `Behavior` API more flexible and less verbose. It's no longer needed to extends `ProtocolLike` trait nor `ProtocolCommand` and `ProtocolEvent` sub-traits.

This has the extra advantage of opening the doors for using serialization libraries like protobuf to generate `Command` and `Event` case classes. 

## Issue #76

The `Actions` API imposed the usage of total function for the declaration of `Command Handlers` and `Event Handlers`.

The main reason for that was that we want to have an API for declaring `Command Handlers` that had the same shape disregarding the return type of the `Command Handlers`.

It was possible to write the following:

```scala
actions
	.handleCommand {
		cmd: SomeCommand => SomeEvent(...)
	}
	.handleCommand {
		cmd: AnotherCommand => 
			Future.successful(SomeOtherEvent(...))
	}
```

Note that the first `Command Handler` returns an unboxed type, while the second has it wrapped in a `Future`.

In order to do it we needed to make use of `ClassTag`s and implicit resolution of `CommandInvokers`. 

At first, this seemed a good idea, but it introduced some complications and unexpected compile errors.

Developers new to the library got puzzled by the fact that it was not possible to pass a `PartialFunction`. Scala developers know that whenever a `Total Function` is required, a `PartialFunction` can be passed. Which is kind of odd, but that is how Scala works. Even more confusing, is that although `Fun.CQRS` were requiring `Total Functions`, it was build `PartialFunction` out of it based on the input type of the function. Hence the need for `ClassTag`.

For instance, this wasn't possible:

```scala
actions
	.handleCommand {
		case SomeCommand(foo) => SomeEvent(foo)
	}
```

Because of this design, it was very hard to build extension points to support new types. Our experimentation building a `Cats` backend was unsatisfactory. Extension libraries wouldn't be able offer the same coding experience when defining `Command Handlers`.

Therefore we decided to move out of implicit `CommandInvokers` and `ClassTag`.

The new API is a little bit more verbose, but it explicit on its intention. From a developer perspective, it's also clear what can be done and how to get it right.