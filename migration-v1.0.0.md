# Migrating to Fun.CQRS 1.0.0

This document covers the migration steps necessary to port your existing **Fun.CQRS** application to version 1.0.0.    

**Fun.CQRS** 1.0.0 brings some breaking changes on the API level. Internally nothing changed, but the user facing API was refactored and you will have to modify your code accordingly. 

We have pulished a [roadmap document](https://github.com/strongtyped/fun-cqrs/blob/develop/roadmap-1.0.0.md) explaining the motivations for those API changes. If you need any further information or help, you can contact us on the gitter channel [![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/strongtyped/fun-cqrs?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge).  

Release 1.0.0 will be rolled out in three milestones: v1.0.0-M1, v1.0.0-M2 and finally v1.0.0. 

This migration guide is broken down into three sections, one for each milestone. As such you can choose to start the migration as soon a milestone is released or wait for the final version.

# Migrating to 1.0.0-M1

This first milestone includes the refactorings related to [#75](https://github.com/strongtyped/fun-cqrs/issues/75) and [#76](https://github.com/strongtyped/fun-cqrs/issues/76) as mentioned in the [roadmap document](https://github.com/strongtyped/fun-cqrs/blob/develop/roadmap-1.0.0.md). 

In this milestone a few classes and traits were removed making **Fun.CQRS** less intrusive

## AggregateLike trait - REMOVED

Previously aggregates were required to implement **Fun.CQRS**' `AggregateLike` trait. This is not needed anymore. This trait was removed. 

Note: we still need to provide a type-safe id that implements `AggregateId` though.

Where you previously had... 

```scala
case class Foo(n: String) extends AggregateLike {
  ...
}
```

You must have...
```scala
case class Foo(n: String)  {
  ...
}
```

## ProtocolLike - REMOVED

The whole idea of defining a `Protocol` object where we had to implement `Commands` and `Events` is gone. 

Where you previously had... 
```scala
object FooProtocol extend ProtocolLike {
  sealed trait FooCommand extends ProtocolCommand
  sealed trait FooEvent extends ProtocolEvent
}
```

You can simply  have...
```scala
object FooProtocol {
  sealed trait FooCommand
  sealed trait FooEvent
}
```

or event better...
```scala
// no FooProtocol wrapper
sealed trait FooCommand
sealed trait FooEvent
```

This gives us a few advantages.  

* user defined `Commands` and `Events` are not bound to **Fun.CQRS** classes
* we can use libraries like Protobuf or Avro to generate `Commands` and `Events` and take advantage of the serialization features provided by those libraries.

## MetadataFacet and Metadata - REMOVED

If you were using `Metadata` and `MetadataFacet` you will have to remove any reference to it. This is very straightforward and we will show how to achieve the same results without depending on them.

If you were NOT using `Metadata`, just skip to the next section.

Typically, `Metadata` was used as following...
```scala
case class FooMetadata(
  aggregateId: FooId,
  commandId: CommandId,
  eventId: EventId     = EventId(),
  date: OffsetDateTime = OffsetDateTime.now(),
  tags: Set[Tag]       = Set()
) extends Metadata
    with JavaTime {
  type Id = FooId
}
  
sealed trait FooEvent extends MetadataFacet[FooMetadata]
```

This should be refactored to:

```scala
case class FooMetadata(
  aggregateId: FooId,
  commandId: CommandId,
  eventId: EventId     = EventId(),
  date: OffsetDateTime = OffsetDateTime.now(),
  tags: Set[Tag]       = Set()
) 
  
sealed trait FooEvent {
  def metadata: FooMetadata
  final def id: EventId = metadata.eventId
  final def aggregateId: FooId = metadata.aggregateId
  final def commandId: CommandId = metadata.commandId
  final def date: OffsetDateTime = metadata.date
  final def tags: Set[Tag] = metadata.tags
}
```
  
You can also replace `EventId` and `CommandId` by your own types if you prefer. Nothing forces you to depend on those types.
   
## Implement Types trait

That new version removes lots of classes and traits, but in order to offer, as much as we can, a type-safe API, we need to glue the parts together. In previous versions this was done by means of traits like `AggregateLike` and `ProtocolLike`. In **Fun.CQRS** v1.0.0 we glue the parts together by means of the `Types` trait. 

The recommended way of using it, is to let the aggregate's companion object extend it. This is not obligatory, but extremely recommended. You can let another object implement it, but: 1) it must be an object (not a class); 2) make sure you have it implicitly in scope or that you pass it explicitly whenever you request a `aggregateRef` (more on this below).

```scala
object Foo extends Types[Foo] {
  type Id = FooId // FooId must extend AggregateId
  type Command	= FooCommand
  type Event = FooEvent
}
```

Letting the companion object implement the `Types` trait gives the additional advantage of bringing an implicit Types[Foo] automatically in scope. This will be needed when requesting `aggregateRefs`.

That's all what we need. 
	
## Actions, Command Handlers and Event Handlers

This is the most fastidious migration bit as it impact all your command and event handlers. The good news is that it's extremely simple and straightforward. Most of them can be done with a find and replace opreration.

### Actions - find-and-replace

This can be done with simple find-and-replace.

Replace each occurrence of 
	`Actions[Foo]` 
by 
	`Foo.actions`

(assuming `Foo` companion object implements `Types`)

### Event Handler - find-and-replace

Event handlers are now `PartialFunctions`. 

Replace each occurrent of 
	`handleEvent { evt: FooEvent ...` 
by 
	`eventHandler { case evt: FooEvent ...`

(pay attention to the method name change)


### Command Handler

Command handlers were also refactored to `PartialFunctions`, however the migration can't be done by find-and-replace, at least not totally. 

Previously Command Handlers declaration required the availabilty of `InvokerDirectives` in the implicit scope. This was need to seamlessly revolve the handlers' return types.

We move out of that approach for many reasons that were explained in the roadmap document that we won't repeat here. Please, consult it for more info.

The migration of the command handlers can be done partially by replacing all occurrences of

`handleCommand { cmd: FooCommand ... `
by 
`commandHandler { case cmd: FooCommand ... `

(pay attention to the method name change)  
  
  
But this is not yet enough. This won't compile.

After that, your command handler may look like this:

```scala 
commandHandler { case cmd: FooCommand => FooEvent(...) } 
```

this will need to be refactored to:

```scala
import io.funcqrs.behavior.handlers._
commandHandler { 
  OneEvent { case cmd: FooCommand => FooEvent(...) }
} 
```

`OneEvent` replaces the former implicit `InvokerDirective` and explicitly instruct **Fun.CQRS** that this Command Handler will return one single unboxed Event (ie: not wrapped in a Option, Try, Future, etc).

Obviously there are other types of `CommandHandlers`.   

* `OneEvent` and `ManyEvents` for unboxed single `Event` or `Seq[Event]`
* `maybe.OneEvent` and `maybe.ManyEvents` for single `Event` or `Seq[Event]` wrapped in a `Option`
* `attempt.OneEvent` and `attempt.ManyEvents` for single `Event` or `Seq[Event]` wrapped in a `Try`
* `eventually.OneEvent` and `eventually.ManyEvents` for single `Event` or `Seq[Event]` wrapped in a `Future`

This part of the new API became more verbose as you can notice, but on the other hand we see the advantage of being explicit on the return types of `CommandHandlers`. It conveys much better its intention and it removes the need of advanced (sometimes complicated) techniques to make it work implicitly. 

Moreover, it opens the door for user defined `CommandHandlers`, for instance: a `validated.OneEvent` can now easily be implemented to return `cats.Validated` or `scalaz.Validation`.

## Behavior DSL

The Behavior DSL changed slightly. Again in the spirit of "being more explicit is better". 

Where you previously had...

```scala
Behavior { 
  createActions(...)
} {
  case foo => foo.someOtherActions
}   
```

You must have...

```scala
Behavior
  .first { 
    createActions(...)
  }
  .andThen {
    case foo => foo.someOtherActions
  }   
```

## Backend Configuration

Also a slight change. Mainly a consequence of dropping `AggregateLike` and `ProtocolLike` and introducing the `Types` trait.



### configuration


Where you previously had...
```scala
backend.configure {
  aggregate[Foo](Foo.behavior)
}
```

You must have ...
```scala
backend.configure {
  aggregate(Foo.behavior)
}
```

### requesting aggregateRef

When requesting an aggregate instead of calling...

```scala
val id = FooId("bar")
backend.aggregateRef[Foo](id)
```

you must call...
```scala
val id = FooId("bar")
backend.aggregateRef[Foo].forId(id)
```

In order to correctly resolve the types the call to `aggregateRef` requires an implicit `Types[Foo]`. This is automatically provided if `Foo`'s companion object implements `Types` (as recommended). How this is achieve is out of scope for this migration guide. Just keep in mind that you get it for 'free' if you follow this recommendation.

If for some reason you really can't or don't want your companion object to implement `Types`, then you need to bring it into the implicit scope yourself or pass it explicitly when calling `aggregateRef`.


# Migrating to 1.0.0-M2

Milestone 2 includes the refactoring of the `Projection` API as mentioned in the [roadmap document](https://github.com/strongtyped/fun-cqrs/blob/develop/roadmap-1.0.0.md).

The first main change to take into account is that querying events is not tight to the backend anymore, but must be defined on a per projection basis. The reason for that is that we want to be able to consume events coming from other systems.

Previously we needed to define `EventsSourceProvider` in the backend to teach it how to query events. For the `AkkaBackend` that was tipically done using the `akka-persistence-query` API. Later, when defining the projection, we needed to provided a `Query` param that could be interpreted by the `EventsSourceProvider`. This design was suboptimal and is therefore removed. 

Instead, we will define a `PublisherFactory` for each specific `Projection`. A `PublisherFactory` is reponsible to provide a `org.reactivestreams.Publisher` that will emit the queried `Events`. 
What happens inside the `PublisherFactory` and the provided `Publisher` is specific to the kind of stream we consume. For instance, are we reading from the local akka journal, then the `Publisher` will probably be based on a Akka Stream `Source`. 

## EventsSourceProvider - REMOVED
You may remove any reference to `EventsSourceProvider`. We will see soon how to define the source of streams based on a `Publisher`.

## Typed Projections

Projetions are now typed. 

Where you previously had:

```scala
import io.funcqrs.Projection
class MyProjection(repo: MyRepo) extends Projection
```

You must now have:

```scala
// Note that the `Projection` moved to package `io.funcqrs.projections`.
import io.funcqrs.projections.Projection
class MyProjection(repo: MyRepo) extends Projection[MyEvent]
```

In this example, the `Projection` is typed on `MyEvent`, but it could be whatever type your `Publisher` will be emitting. For instance, you can define an envelope type `case class MyEnvelope(offset: Long, event:MyEvent)`, let the `Publisher` emit it and receive the offset in your `Projection`.

## Defining `PublisherFactory`

A `PublisherFactory` is trait you need to implement that will work as a factory for a `org.reactivestreams.Publisher`. 

```scala
package io.funcqrs.projections
import org.reactivestreams.Publisher

trait PublisherFactory[O, E] {
  def from(offset: Option[O]): Publisher[(O, E)]
}
```

When using the `AkkaBackend` we can define a `PublisherFactory` as following:

```scala
// akka api for querying events
def publisherForRaffle = {
  val readJournal =
	PersistenceQuery(actorSys)
      .readJournalFor[LeveldbReadJournal](LeveldbReadJournal.Identifier)
    		
  new PublisherFactory[Long, RaffleEvent] {
    override def from(offset: Option[Long]): Publisher[(Long, RaffleEvent)] = {
      // map long offset to akka new Sequence offset
	  val akkaOffset = offset.map(Sequence).getOrElse(Sequence(0))
		
	  readJournal
        .eventsByTag(Raffle.tag.value, akkaOffset)
        .map { akkaEnvelope =>
           // receive akka envelope and emit a (Long, RaffleEvent)
           val Sequence(value) = akkaEnvelope.offset
           (value, akkaEnvelope.event.asInstanceOf[RaffleEvent])
		}
		// integration point: from akka Source to reactive streams Publisher
        .runWith(Sink.asPublisher(false))
	}
  }
}    		
```

## Configuring Projection

The next step is to glue the `Projection` with the `PublisherFactory`.

Where you previously had:

```scala
backend.configure {
  projection(
     query      = QuerySelectAll,
     projection = new RaffleViewProjection(raffleViewRepo),
     name       = "RaffleViewProjection"
   )
} 
```

You must now have:

```scala
backend.configure {
  projection(
     projection = new RaffleViewProjection(raffleViewRepo),
     publisherFactory = publisherForRaffle,
     name       = "RaffleViewProjection"
   )
} 
```
Note that the `query` is gone because it's now defined inside the `PublisherFactory`. The factory is hence specific to a given projection query. 

## OffsetPersistenceStrategy

The `BackendOffsetPersistence` is now deprecated. If you were using it, you should now declare it explicitly.

Where you previously had:

```scala
backend.configure {
  projection(...).withBackendOffsetPersistence()
}
```

You must have:

```scala
backend.configure {
  projection(...)
    .withCustomOffsetPersistence(
      AkkaOffsetPersistenceStrategy.offsetAsLong(actorSys, "RaffleViewProjection")
    )
}
```
Very important, you MUST pass to the strategy the same name you have chosen for your `Projection`. Failing to do so will cause the offset to be saved on another name space and as a consequence, when restarting your system for the first time after migration you will replay the `Projection` from scratch.

Note that `AkkaOffsetPersistenceStrategy.offsetAsLong`, saves the offset as an `LastProcessedEventOffset` event on the journal. That's a quick-win, but we really recommend to implement your own `OffsetPersistenceStrategy` that saves the offset to a offset table (Cassandra, JDBC or whatever db you may be using). For that reason, we have no plans to provide a  `AkkaOffsetPersistenceStrategy.offsetAsTimeBasedUUID`. We are only keeping it for backward compatibility reasons.

# Migrating to 1.0.0 (Final)

TODO: not yet released - this guide will be filled when we release 1.0.0 (Final)
