---
layout: home
---

# Introduction

**Fun.CQRS** is a Scala CQRS/ES framework. It provides the basic blocks to build event driven aggregates with *Event Sourcing*.

*Fun.CQRS* provides a out-of-the-box `AkkaBackend` and a `InMemoryBackend` for testing. However, it’s designed as such that other backend implementations are possible. For instance, an alternative Akka backend based on Eventuate, a Slick backend or RxScala backend could be implementated and plugged in easily.


## Project Information

**Fun.CQRS** is open source software. Source code is available at:  
https://github.com/strongtyped/fun-cqrs (development branch)

Stable and released branch can be found at:  
https://github.com/strongtyped/fun-cqrs/tree/master

**Project artifact** +
The artifacts are published to Sonatype Repository. Simply add the following to your build.sbt.

```scala
libraryDependencies += "io.strongtyped" %% "fun-cqrs-akka" % "{{site.project.version}}"
```

If you want to hack **Fun.CQRS** and develop your own backend, you can import only the core module.
The core module does NOT include the Akka backend.

```scala
libraryDependencies += "io.strongtyped" %% "fun-cqrs-core" % "{{site.project.version}}"
```

