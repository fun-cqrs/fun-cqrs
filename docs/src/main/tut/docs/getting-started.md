---
layout: docs
title: Getting Started
---

# Getting Started

To get you started with **Fun.CQRS** we will build a simple **Lottery** application. This example is also available as a runnable sample in the project repository [GitHub](https://github.com/strongtyped/fun-cqrs/tree/develop/samples/lottery/src).

We will first concentrate on the modeling of the **Command Side**. In which we will define the **Lottery Aggregate**, its **Protocol** and its **Behavior**. We will learn how to configure the `InMemoryBackend` and write tests to verify the Lottery's behavior.

Next we will build the **Query Side** by defining one **View Model** and a **Projection** that consumes events and creates/updates the **LotteryDetails** model. We will reconfigure the `InMemoryBackend` for the **Query Side** and extend the tests.

Last, we will plug the code we wrote with the `AkkaBackend` and run it in a `main` method.  

During this tutorial we will cover many aspects of **Fun.CQRS**, but we won't go deep into the details. The goal is to get you started by building a first application. Links to more detailed documentation will be provided along the tutorial. 


1. [Aggregates - Command Side](aggregates.html) 
2. [Configuring the `InMemoryBackend`](in-memory-backend.html) (todo)
3. [Command Side Tests](command-side-tests.html) (todo)
4. [Projection - Query Side](projections.html) (todo)
5. [Query Side Tests](query-side-tests.html) (todo)
6. [Configuring the `AkkaBackend`](akka-backend.html) (todo)