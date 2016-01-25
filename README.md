# Fun.CQRS

Small library for building CQRS application using Scala in combination with Akka.

[![Build Status](https://travis-ci.org/strongtyped/fun-cqrs.svg?branch=develop)](https://travis-ci.org/strongtyped/fun-cqrs)

[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/strongtyped/fun-cqrs?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge)


## Project artifact

The artifacts are published to Sonatype Repository. Simply add the following to your build.sbt.

```scala
libraryDependencies += "io.strongtyped" %% "fun-cqrs-akka" % "0.3.0-SNAPSHOT"
```

If you want to hack **Fun.CQRS** and develop your own backend, you can import only the core module.
The core module does NOT include the Akka backend.

```scala
libraryDependencies += "io.strongtyped" %% "fun-cqrs-core" % "0.3.0-SNAPSHOT"
```

## Documentation

Documentation is in the process of being written.  
A preview is available [here](http://htmlpreview.github.io/?https://github.com/strongtyped/fun-cqrs/blob/develop/docs/asciidoctor/index.html).

For the moment the best way to learn how to use Fun.CQRS is to check the lottery sample under fun-cqrs/samples/lottery.

You can also watch these two videos to better understand the philosophy behind Fun.CQRS.

[Devoxx 2015](https://www.youtube.com/watch?v=fQkKu4tTgCE) (2h45m)  
[Scala Exchange 2015](https://skillsmatter.com/skillscasts/7047-building-a-cqrs-application-using-the-scala-type-system-and-akka) (45m)  
 Note that this two presentations contains code that have been refactored in the mean time. However, you will get a good picture of the available features by watching the videos. 
  

## Contribution policy

Contributions via GitHub pull requests are gladly accepted from their original author. Along with any pull requests, please state that the contribution is your original work and that you license the work to the project under the project's open source license. Whether or not you state this explicitly, by submitting any copyrighted material via pull request, email, or other means you agree to license the material under the project's open source license and warrant that you have the legal authority to do so.
