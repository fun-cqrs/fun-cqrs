---
layout: docs
title: Backends
---

# Fun.CQRS Backends

On the [Aggregates](aggregates.md) section we learned how to design an `Aggregate` in terms of `Command Handlers` and `Event Handlers`. However, we still don't have the means to work with it. 

To understand the role of a backend we must first go back to the basic CQRS/ES operations that we are trying to abstract over. In **CQRS/ES** we can devise two basic functions:

```scala
// basic Command Handler
(State[Aggregate], Command) => F[Events]
// basic Event Handler
(State[Aggregate], Event) => State[Aggregate]
```
> Note: in **Fun.CQRS** we don't directly define functions like those. Instead we use the **Behavior DSL** that allow us to define them by concentrate only on the parts that are domain specific.

The `Command Handler` receives the state of an `Aggregate` and a new `Command` and emits one or more `Events`. `Events` are wrapped on a `F[_]`, where `F[_]` can be any of: `Identity`, `Try`, `Future` and  `Option`.

The `Event Handler` receives the state of an `Aggregate` and an `Event` and produces a new `Aggregate` state.

Given those two functions, the role of the backend is: 

1. Provide the current `State[Aggregate]`
2. Understand how to deal with `F[_]` and "interpret" it
3. Persist the `Events` eventually emitted by the `Command Handler`
4. Update the current `State[Aggregate]` by applying the emitted `Events` to it

As such, a **Fun.CQRS** backend is where IO and persistence take place. 

A backend is also tight to a `F[_]` and will lift all possible incarnations of `F[_]` to its own `F[_]`. For instance, for the `AkkaBackend` `F` is as `Future`. If you define a `Command Handler` that returns a `Try` and use it with the `AkkaBackend`, you will get a `Future` instead. 

More over, a backend does not let you work directly with an `Aggregate`. The principle is pretty much inspired in **Akka**. You request an `AggregateRef`, you send `Commands` to it and you don't manipulate the `Aggregate` directly.

## Configuration

In orde to use an `Aggregate` we must first configure it on a **Backend**. This is done only once and is supposed to happen when bootstrapping the application.
(we see how to configure it on tutorial section: [Command Side Tests](command-side-tests.html))

Once the aggregate is configured we can ask the backend for instances of `AggregateRef` to work with. 

Similar to Akka, we don't work directly with an `Aggregate`, but with a reference to it. The `Aggregate` itself lives inside the backend and we send commands to each via an `ask` (?) and  `tell` (!). Again shameless inspired by Akka. 

The only difference is that an `AggregateRef` can only receive commands previously defined by its `Protocol` and will only emit `Events` from its `Protocol` as well. As such, an `AggregateRef` is typed on its `Protocol`.
 
## InMemoryBackend

For test purposes we provide a `InMemoryBackend` where `Events` and `Aggregate` state are 'persisted' in-memory. 

The `InMemoryBackend` defines `F[_]` as `Identity` ([see](https://github.com/strongtyped/fun-cqrs/blob/develop/modules/core/src/main/scala/io/funcqrs/interpreters/package.scala)).  

`Identity` can NOT express an error condition and therefore it will block for `Command Handlers` returning `Futures` and it will throw exceptions for failed `Futures` and `Trys`.

A usage example for the `InMemoryBackend` can be found on tutorial section on [Command Side Tests](command-side-tests.html)  

## AkkaBackend

The `AkkaBackend` is intended for production use and defines `F[_]` as `Future`.  

The `Aggregate` lives inside an `PersistentActor` and the backend guarantees that at most one instance (per `AggregateId`) is loaded in-memory. 

`Events` are persisted using **akka-persistence**.  

Detailed documentation about the `AkkaBackend` can be found [here](akkab-backend.html)

