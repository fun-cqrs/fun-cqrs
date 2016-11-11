---
layout: docs
title: Aggregates
---


# The Lottery Aggregate

A **Lottery** is an aggregate with three possible **states** modeled as an **Algebraic Data Type**: `EmptyLottery`, `NonEmptyLottery` and `FinishedLottery`.

A User can create a new lottery game which is initiated as an `EmptyLottery`

A **Lottery** aggregate has the following requirements and sum types:

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

### AggregateId and Protocol

Before we start coding the Lottery aggregate we must define an `AggregateId` for it and it's `Protocol` (that we will fill latter).

```scala
import io.funcqrs.AggregateId

/** Defines the a type-safe ID for Lottery Aggregate */
case class LotteryId(value: String) extends AggregateId
``` 

```scala
import io.funcqrs.ProtocolLike
import java.time.OffsetDateTime

/** Defines the Lottery Protocol, 
   all Commands it may receive and Events it may emit */
object LotteryProtocol extends ProtocolLike {
  
  // commands
  sealed trait LotteryCommand extends ProtocolCommand
  case object CreateLottery extends LotteryCommand
  case class AddParticipant(name: String) extends LotteryCommand
  case class RemoveParticipant(name: String) extends LotteryCommand
  case object RemoveAllParticipants extends LotteryCommand
  case object Run extends LotteryCommand  
  
  // events
  sealed trait LotteryEvent extends ProtocolEvent {
    def lotteryId: LotteryId
  }
  case class LotteryCreated(lotteryId: LotteryId) extends LotteryEvent
  sealed trait LotteryUpdateEvent extends LotteryEvent
  case class ParticipantAdded(name: String, lotteryId: LotteryId) extends LotteryUpdateEvent
  case class ParticipantRemoved(name: String, lotteryId: LotteryId) extends LotteryUpdateEvent
  case class WinnerSelected(winner: String, date: OffsetDateTime, lotteryId: LotteryId) extends LotteryUpdateEvent  
}
```

And finally the `Lottery` base trait and its variants.

```scala
import io.funcqrs.AggregateLike

sealed trait Lottery extends AggregateLike {
  type Id       = LotteryId
  type Protocol = LotteryProtocol.type
}
case class EmptyLottery(id: LotteryId) extends Lottery
case class NonEmptyLottery(participants: List[String], id: LotteryId) extends Lottery
case class FinishedLottery(winner: String, id: LotteryId) extends Lottery
```

### Aggregate Behavior

Next we must define how the **Seed Event** is generated. By **Seed Event** we understand the event that will generate the first instance of a given **Aggregate**. Since `Events` are generated from `Commands` we can consider that there is also a **Seed Command**, the one that will generate the **Seed Event**. 

In analogy with a classical OOP modeling, the **Seed Command** is the equivalent of constructor validator that checks if the passed data is valid to build the object instance while the **Seed Event** is the reification of the constructor parameters. Once we have the **Seed Event** we can safely build the **Aggregate** instance.

A `Behavior` is defined as two main functions:  

 * The first function we call the `seedFunction` or `factory` and is defined as:  
`() => Actions[A]`. Where `A` is the Aggregate type. This function express the fact that we don't have yet an `Aggregate`. We have nothing `()` and we return a `Actions[A]`.
 
* The second function is a `PartialFunction[A, Actions[A]]` and is used once we do have an `Aggregate` instance.

We will start by defining the `factory` function and an almost empty `Behavior`.
By convention we define it in the `Aggregate` companion object.

```scala
import io.funcqrs.behavior._
import LotteryProtocol._

object Lottery {
      
  def behavior(lotteryId: LotteryId): Behavior[Lottery] =
    Behavior {
      // () => Actions[Lottery]
      // defines seed command and event handlers
      actions[Lottery] 
        .handleCommand { 
          cmd: CreateLottery.type => LotteryCreated(lotteryId)
        }
        .handleEvent { 
          evt: LotteryCreated => EmptyLottery(id = lotteryId)
        }
    } {
      // PartialFunction[Lottery, Actions[Lottery]]
      // once we have an aggregate we can define actions for each of its variants
      // for the moment we let them all empty
      case lottery: EmptyLottery    => Actions.empty[Lottery]
      case lottery: NonEmptyLottery => Actions.empty[Lottery]
      case lottery: FinishedLottery => Actions.empty[Lottery]
    }      
}      
```
 
As expected, the **Seed Command** generates one single `Event`, which is our **Seed Event**. The `Event Handler` defined for it will instantiate the `Lottery` aggregate as an `EmptyLottery`.

The second function block is defined, but the `Actions` are empty.

The `Behavior` as defined here is not very useful as we can only instantiate the an `EmptyLottery`.

### EmptyLottery Actions
To further implement our aggregate, we will define the applicable `Command Handlers` and `Event Handlers` for an `EmptyLottery`.

From the already defined `Commands` and `Events` there are a couple that are clearly not applicable, for example: 
Does it make sense to `Run` a `Lottery` without any participants? And does it make sense to `RemoveParticipant` from an `EmptyLottery`? Obviously not, therefore we will only define the possible `Actions` the the context of an `EmptyLottery`.

```scala
import LotteryProtocol._
case class EmptyLottery(id: LotteryId) extends Lottery {

  def acceptParticipants =
    actions[Lottery]
      .handleCommand {
        cmd: AddParticipant => ParticipantAdded(cmd.name, id)
      }
      .handleEvent {
        evt: ParticipantAdded =>
          // NOTE: that as soon we have a participant, the Lottery transitions to NonEmptyLottery
          NonEmptyLottery(participants = List(evt.name), id = id)
      }
}
```

**The updated Behavior PartialFunction**

```scala
Behavior {
 // omitted for brevity
} {
  // PartialFunction[Lottery, Actions[Lottery]]
  case lottery: EmptyLottery    => lottery.acceptParticipants
  case lottery: NonEmptyLottery => Actions.empty[Lottery]
  case lottery: FinishedLottery => Actions.empty[Lottery]
}    
```

### NonEmptyLottery Actions

The `NonEmptyLottery` is the main variant. It handles all possible `Commands` and `Events`. The `Actions` are defined in separated blocks each representing one logical set of `handlers`. Later they are all concatenated together to form one single `Action`.

We will show each block separately so we can comment which of them, but keep in mind that all the actions in this example are defined inside `NonEmptyLottery`.

---
#### Rejecting Double Booking

```scala
import LotteryProtocol._
import io.funcqrs.CommandException

case class NonEmptyLottery(participants: List[String], id: LotteryId) extends Lottery {
  ...
  /** Action: reject double booking. Can't add the same participant twice */
  def rejectDoubleBooking = {
    action[Lottery]
      .rejectCommand {
        // can't add participant twice
        case cmd: AddParticipant if participants.contains(name) =>
          new CommandException(s"Participant ${cmd.name} already added!")
      }
  }
  ...
}
```

The above code fragment introduces a new kind of `Command Handler` called a `Guard`. A `Guard` is a `Command Handler` that will never emit `Events`, but will return an `Exception` instead. `Guard Command Handlers` are specially useful for situations where we can easily check if the command is valid or not. 

Contrary to a regular `Command Handler`, a `Guard` receives a `PartialFunction` and therefore we can implement the guard like we usually do when `Pattern Matching`.

In case the guard condition holds, the command is rejected with a `CommandException`. You can return any exception here, but it's recommend to use `io.funcqrs.CommandException` as no [stacktrace overhead](http://www.scala-lang.org/api/current/scala/util/control/NoStackTrace.html). 

Exceptions returned by `Guard` are only used to express errors and are never thrown (unless you choose for the `IdentityInterpreter`, [read more](in-memory-backend.html#identity-interpreter).


---

#### Accept Participants


```scala
import LotteryProtocol._
case class NonEmptyLottery(participants: List[String], id: LotteryId) extends Lottery {
  ...
  /** Action: add a participant */
  def acceptParticipants =
    actions[Lottery]
      .handleCommand {
        cmd: AddParticipant => ParticipantAdded(cmd.name, id)
      }
      .handleEvent {
        evt: ParticipantAdded => copy(participants = evt.name :: participants)
      }
  ...
}
```

Adding an new participants is straightforward and very similar to the definition in `EmptyLottery` except that the `Event Handler` updates the `List` of participants instead.

The `Command Handler` is exactly the same and we could have code it elsewhere for better reuse, but for clarity sake we choose to repeat it here.

--- 

#### Remove Participants


```scala
import LotteryProtocol._
case class NonEmptyLottery(participants: List[String], id: LotteryId) extends Lottery {
  ...
  /** Action: remove participants (single or all) */
  def removeParticipants =
    actions[Lottery]
      // removing participants (single or all) produce ParticipantRemoved events
      .handleCommand {
        cmd: RemoveParticipant => ParticipantRemoved(cmd.name, id)
      }
      .handleCommand {
        // will produce a List[ParticipantRemoved]
        cmd: RemoveAllParticipants.type =>
          this.participants.map { name => ParticipantRemoved(name, id) }
      }
      .handleEvent {
        evt: ParticipantRemoved =>
          val newParticipants = participants.filter(_ != evt.name)
          // NOTE: if last participant is removed, transition back to EmptyLottery
          if (newParticipants.isEmpty)
            EmptyLottery(id)
          else
            copy(participants = newParticipants)
      }
  ...
}
```  

The remove participants definition has two `Command Handlers`, but only one `Event Handler`. The reason for that is that both `Command Handlers` are emitting the same type of `Event`, `ParticipantRemoved` event. Therefore we only need to define one `Event Handler`. 

Also important is that we make sure that we transition back to an `EmptyLottery` in case we remove the last participant.

---

### Running the Lottery

```scala
import LotteryProtocol._
case class NonEmptyLottery(participants: List[String], id: LotteryId) extends Lottery {
  ...        
  /** Action: run the lottery */
  def runTheLottery =
    actions[Lottery]
      .handleCommand {
        cmd: Run.type =>
          val index = Random.nextInt(participants.size)
          val winner = participants(index)
          WinnerSelected(winner, OffsetDateTime.now, id)
      }
      .handleEvent {
        // transition to end state on winner selection
        evt: WinnerSelected => FinishedLottery(evt.winner, id)
      }
  ...    
}
```  

Running the Lottery is also straightforward. An random participant is selected in the `Command Handler` and a single event is emitted. The `Event Handler` transition the Lottery to `FinishedLottery`.

Note that `FinishLottery` does not have the list of participants anymore. Only the selected winner. The reason for that is that when modeling an `Aggregate` we should only preserve data needed for future `Command` validations. `Aggregates` are not queried and therefore should not hold useless data.

--- 

#### Concatenating Actions

Finally we can take all actions definitions from `NonEmptyLottery`, concatenate them all together and add it to the final `Behavior`

```scala
Behavior {
 // omitted for brevity
} {
  // PartialFunction[Lottery, Actions[Lottery]]
  case lottery: EmptyLottery    => lottery.acceptParticipants

  case lottery: NonEmptyLottery => 
      // actions can be concatenated using the ++ operator
      lottery.rejectDoubleBooking ++
        lottery.acceptParticipants ++
        lottery.removeParticipants ++
        lottery.runTheLottery
        
  case lottery: FinishedLottery => Actions.empty[Lottery]
}  
```


### FinishedLottery Action

```scala
case class FinishedLottery(winner: String, id: LotteryId) extends Lottery {

  /** Action: reject all */
  def rejectAllCommands =
    action[Lottery]
      .rejectCommand {
        // no command can be accepted after having selected a winner
        case anyCommand  =>
          new LotteryHasAlreadyAWinner(s"Lottery has already a winner and the winner is $winner")
      }
}
```

A `FinishedLottery` represent the final state and end-of-life for a `Lottery` game. It has only one `Guard` that reject all incoming commands.


### Final Behavior

Finally, we have the total `Behavior` our `Lottery` aggregate. 
We have defined the seed actions to initialize it and defined all possible actions of each of it's variants.

```scala
    Behavior {
      // () => Actions[Lottery]
      // defines seed command and event handlers
      actions[Lottery] 
        .handleCommand { 
          cmd: CreateLottery.type => LotteryCreated(lotteryId)
        }
        .handleEvent { 
          evt: LotteryCreated => EmptyLottery(id = lotteryId)
        }
    } {
      case lottery: EmptyLottery =>
        lottery.canNotRunWithoutParticipants ++
          lottery.acceptParticipants

      case lottery: NonEmptyLottery=>
        lottery.rejectDoubleBooking ++
          lottery.acceptParticipants ++
          lottery.removeParticipants ++
          lottery.runTheLottery

      case lottery: FinishedLottery => lottery.rejectAllCommands
    }
```

---
On the next chapter we learn how to configure the `InMemoryBackend` for testing.  
[Configuring the `InMemoryBackend`](in-memory-backend.html)
