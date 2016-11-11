---
layout: docs
title: Akka Backend
---

When using the `AkkaBackend`, Aggregates are immutable classes (case class) that live inside an `Actor`. You don't have to deal much with Akka and it's powerful abstractions, instead you concentrate in modeling your aggregate behavior and its protocol (`Commands` and `Events`). However you still need a minimal understanding of how Akka works and how to configure Akka Persistence to use your persistence plugin of choice.

That said, in **Fun.CQRS**, Aggregates are NOT Actors. The **Actor System** is used as a middleware to manage the aggregates, hold them in-memory, store events, recover aggregate state and generate read models through  **Event Projections**


## Work in progress

For the moment check:
[Sample on GitHub](https://github.com/strongtyped/fun-cqrs/tree/develop/samples/lottery/src)  or our Gitter channel [![Gitter](https://badges.gitter.im/strongtyped/fun-cqrs.svg)](https://gitter.im/strongtyped/fun-cqrs?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge)