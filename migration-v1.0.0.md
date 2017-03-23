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

This should be easily refactored to:

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

The recommended way of using it, is to let the aggregate's companion object extend it. This is not obligatory, but extremelly recommended. You can let another object implement it, but: 1) it must be an object (not a class); 2) make sure you have it implicitly in scope or that you pass it explicitly whenever you request a `aggregateRef` (more on this below).

```scala
object Foo extends Types[Foo] {
  type Id = FooId // FooId must extend AggregateId
  type Command	= FooCommand
  type Event = FooEvent
}
```

Letting the companion object implement the `Types` trait gives the aditional advantage of bringing an implicit Types[Foo] automatically in scope. This will be needed when requesting `aggregateRefs`.

That's all what we need. 
	
## Actions, Command Handlers and Event Handlers

This is the most fastidious migration bit as it impact all your command and event handlers. The good news is that it's extremelly simple and straightforward. Most of them can be done with a find and replace opreration. 

### Actions - find-and-replace

This can be done with simpel find-and-replace. 

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

Priviously Command Handlers declaration required the availabilty of `InvokerDirectives` in the implicit scope. This was need to seamlessly revolve the handlers' return types. 

We move out of that approach for many reasons that were explained on the roadmap document that we won't repeat here. Please, consult it for more info.

The migration of the command handlers can be done partially by replacing all occurences of

`handleCommand { cmd: FooCommand ... `
by 
`commandHandler { case cmd: FooCommand ... `

(pay attention to the method name change)  
  
  
But this is not yet enough. This won't compile.

After that your command handler may have this shape:

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

In order to correctly resolve the types the call to `aggregateRef` requires an implicit `Types[Foo]`. This is automatically provided if `Foo`'s companion object implements `Types` (as recommended). How this is achieve is out of scope for this migration guide. Just keep in mind that you get it for 'free' if you follow this recommendatoin.

If for some reason your realy can't or don't want your companion object to implement `Types`, then you need to bring it into the implicit scope yourself or pass it explicit when calling `aggregateRef`.


# Migrating to 1.0.0-M2

TODO: not yet released - this guide will be filled when we release 1.0.0-M2

# Migrating to 1.0.0 (Final)

TODO: not yet released - this guide will be filled when we release 1.0.0 (Final)
