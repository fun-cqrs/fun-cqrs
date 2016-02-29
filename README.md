# Fun.CQRS


[![Build Status](https://travis-ci.org/strongtyped/fun-cqrs.svg?branch=develop)](https://travis-ci.org/strongtyped/fun-cqrs) [![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/strongtyped/fun-cqrs?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge)

**Fun.CQRS** is a Scala library for building CQRS/ES application. It provides the basic blocks to build event driven aggregates with **Event Sourcing**.

**Fun.CQRS** provides a out-of-the-box `AkkaBackend` and a `InMemoryBackend` for testing. However, it's designed as such that other backend implementations are possible. For instance, an alternative Akka backend based on [Eventuate](https://github.com/RBMHTechnology/eventuate), a Slick backend or RxScala backend could be implementated and plugged in easily.

When using the `AkkaBackend`, Aggregates are immutable classes (case class) that live inside an `Actor`. You don't have to deal much with Akka and it's powerful abstractions, instead you concentrate in modeling your aggregate behavior and its protocol (`Commands` and `Events`). However you still need a minimal understanding of how Akka works and how to configure Akka Persistence to use your persistence plugin of choice.

That said, in **Fun.CQRS**, Aggregates are NOT Actors. The **Actor System** is used as a middleware to manage the aggregates, hold them in-memory, store events, recover aggregate state and generate read models through  **Event Projections**


## Project artifact

The artifacts are published to Sonatype Repository. Simply add the following to your build.sbt.

```scala
libraryDependencies += "io.strongtyped" %% "fun-cqrs-akka" % "0.4.0"
```

If you want to hack **Fun.CQRS** and develop your own backend, you can import only the core module.
The core module does NOT include the Akka Backend.

```scala
libraryDependencies += "io.strongtyped" %% "fun-cqrs-core" % "0.4.0"
```

## Documentation

The documentation is published [here](http://www.funcqrs.io).

There is also a sample application  under fun-cqrs/samples/lottery.

You can also watch these two videos to better understand the philosophy behind Fun.CQRS.

[Devoxx 2015](https://www.youtube.com/watch?v=fQkKu4tTgCE) (2h45m)  
[Scala Exchange 2015](https://skillsmatter.com/skillscasts/7047-building-a-cqrs-application-using-the-scala-type-system-and-akka) (45m)  
 Note that this two presentations contains code that have been refactored in the mean time. However, you will get a good picture of the available features by watching the videos. 
  

## Contribution policy

Contributions via GitHub pull requests are gladly accepted from their original author. Along with any pull requests, please state that the contribution is your original work and that you license the work to the project under the project's open source license. Whether or not you state this explicitly, by submitting any copyrighted material via pull request, email, or other means you agree to license the material under the project's open source license and warrant that you have the legal authority to do so.
