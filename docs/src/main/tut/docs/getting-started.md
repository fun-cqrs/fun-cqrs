---
layout: docs
title: Getting Started
---

To get you started with **Fun.CQRS** we will build a simple **Lottery** application. This example is also available as a runnable sample in the project repository [GitHub](https://github.com/strongtyped/fun-cqrs/tree/develop/samples/lottery/src).

We will first concentrate on the modeling of the **Command Side**. In which we will define the **Lottery Aggregate**, its **Protocol** and its **Behavior**. We will learn how to configure the `InMemoryBackend` and write tests to verify the Lottery's behavior.

Next we will build the **Query Side** by defining one **View Model** and a **Projection** that consumes events and creates/updates the **LotteryDetails** model. We will reconfigure the `InMemoryBackend` for the **Query Side** and extend the tests.

Last, we will plug the code we wrote with the `AkkaBackend` and run it in a `main` method.  

During this tutorial we will cover many aspects of **Fun.CQRS**, but we won't go deep into the details. The goal is to get you started by building a first application. Links to more detailed documentation will be provided along the tutorial. 


## The Lottery Aggregate

A **Lottery** is an aggregate with three possible **states** modeled as an **Algebraic Data Type**: `EmptyLottery`, `NonEmptyLottery` and `FinishedLottery`.

A **Lottery** aggregate has the following requirements:

#### Initial State 
A User can create a new lottery game which is initiated as an `EmptyLottery`

#### EmptyLottery
  - A User can add the first lottery participants  
    After which it transitions to `NonEmptyLottery`  

#### NonEmptyLottery
- A User can add more participants  
  **pre-condition**: a participant can only be added once and therefore `AddParticipant` command must be rejected if participant have already subscribed.
    
- A User can remove one or more participants  
  In case the last participant is removed, the `Lottery` becomes an `EmptyLottery`

- A User can run the lottery which will select a winner  
  After which it transitions to `FinishedLottery` state.

#### FinishedLottery

A `FinishedLottery` have reached its end-of-life and it must reject any new `Commands`

---

Before we start coding the Lottery aggregate we must define an `AggregateId` for it and it's `Protocol` (that we will fill latter).

```
import io.funcqrs.AggregateId

/** Defines the a type-safe ID for Lottery Aggregate */
case class LotteryId(value: String) extends AggregateId
``` 

```
import io.funcqrs.ProtocolLike

/** Defines the Lottery Protocol, 
   all Commands it may receive and Events it may emit */
object LotteryProtocol extends ProtocolLike
```

And finally the `Lottery` base trait and its variants.

```
import io.funcqrs.AggregateLike

sealed trait Lottery extends AggregateLike {
  type Id       = LotteryId
  type Protocol = LotteryProtocol.type
}
case class EmptyLottery(id: LotteryId) extends Lottery
case class NonEmptyLottery(participants: List[String], id: LotteryId) extends Lottery
case class FinishedLottery(winner: String, id: LotteryId) extends Lottery
```
