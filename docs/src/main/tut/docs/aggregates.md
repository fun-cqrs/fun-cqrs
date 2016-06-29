---
layout: docs
title: Aggregates
---
### Type-save IDs
**Fun.CQRS** uses type-safe IDs for aggregates. The ID itself must be of type String, but it must be wrapped in a aggregate specific ID type to be used by the system. As such, we avoid ID clashing and it provides better logging as we can clearly see in the logs from each aggregate it comes from.

First we define a type representing the Aggregate ID. We define it by extending the trait `io.funcqrs.AggregateId`.

### Protocol

A `Protocol` is the set of `Commands` and `Events` for a given Aggregate. An Aggregate can only receive `Commands` from its `Protocol`. Its command handlers can only emit `Events` that belong to this same `Protocol`. And finally, event listeners that will instantiate or update an `Aggregate` can only react to `Events` define by the `Protocol`.

Therefore, an Aggregate's Protocol defines the totality of operations and effects of a given Aggregate.

The code below demonstrates how to define a protocol.


### Actions: Command and Event Handlers

### Behavior


Up to now, we have an Aggregate with a type-safe **Id** and its **Protocol** defining all possible `Commands` and `Events`, but we still need to define how each `Command` will be handled, how the `Events` will be generated and how it will react to each `Events`. That all respecting the invariants of the `Lottery` aggregate.

We need to define the Lottery's **Behavior**. +
A `Behavior` itself is just a `PartialFunction` from `State[A] => Actions[A]` where A is the aggregate type (the Lottery in this example), `State` is whether `Uninitialized` or `Initialized` and `Actions` is the set of **Command Handlers** and **Event Handlers** (the Actions) that are applicable for a given `State`.

There are two kinds of **Command Handlers**. Handlers that produce `Events` and handlers that reject commands, also know as 'guard clauses'.


The code below demonstrate how we can build a `Behavior` with the help of the **Behavior DSL**.

But before we start we will revisit the `Lottery` aggregate and add some methods returning `Actions` that we can compose and use when defining the final `Behavior`.

We define some **Command Handlers** that reject a command based on the `Command` data or/and aggregate state.

Followed by some `Actions` defining **Command Handlers** and **Event Handlers** covering the remaning operations of a `Lottery`.
[source,scala]
----
include::../../samples/lottery/src/main/scala/lottery/domain/model/Lottery.scala[tags=lottery-aggregate-actions]
----

The `Behavior` can now be defined as a `PartialFunction` from different `States`: `Uninitialized` and many variations of `Initialized` to `Actions`.


(ref: https://github.com/strongtyped/fun-cqrs/blob/master/samples/lottery/src/main/scala/lottery/domain/model/Lottery.scala[Lottery.scala, window="_blank"])

<1> The creation case is a special one. It plays the role of Aggregate Factory and therefore cannot be defined in the `Lottery` itself.
<2> To make sure we won't run a `Lottery` twice, the next case to match must check if the `Lottery` has already a winner. If that's the case we reject all subsequent commands. The `Lottery` reached its 'end-of-life', it cannot evolve its state anymore.
<3> While the `Lottery` has no participants, it can only accept subscriptions and reject the `Run` command. Therefore this state is the composition of `canNotRunWithoutParticipants` and `acceptParticipants` Actions.
<4> As soon the `Lottery` has at least one participant it can accept new participants (`acceptParticipants`), reject double booking (`rejectDoubleBooking`), remove participants (`removingParticipants`) and finally we can select a winner by running it (`runTheLottery`).
