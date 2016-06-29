---
layout: docs
title: Akka Backend
---

When using the `AkkaBackend`, Aggregates are immutable classes (case class) that live inside an `Actor`. You don't have to deal much with Akka and it's powerful abstractions, instead you concentrate in modeling your aggregate behavior and its protocol (`Commands` and `Events`). However you still need a minimal understanding of how Akka works and how to configure Akka Persistence to use your persistence plugin of choice.

That said, in **Fun.CQRS**, Aggregates are NOT Actors. The **Actor System** is used as a middleware to manage the aggregates, hold them in-memory, store events, recover aggregate state and generate read models through  **Event Projections**