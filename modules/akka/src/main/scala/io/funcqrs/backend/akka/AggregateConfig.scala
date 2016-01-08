package io.funcqrs.backend.akka

import io.funcqrs._
import io.funcqrs.behavior.Behavior


// ================================================================================
// support classes and traits for AggregateService creation!

trait AggregateConfig[A <: AggregateLike] {

  def name: Option[String]

  def behavior: (A#Id) => Behavior[A]

  def idStrategy: AggregateIdStrategy[A]

  def withName(name: String): AggregateConfig[A]

  /**
    * Configure Aggregate to use an [[AssignedIdStrategy]].
    *
    * Aggregate Ids are defined externally.
    */
  def withAssignedId: AggregateConfigWithAssignedId[A] = {
    AggregateConfigWithAssignedId(name, behavior, AssignedIdStrategy[A])
  }

  /**
    * Configure Aggregate to use an [[GeneratedIdStrategy]].
    * On each create command, a new unique Id will be generated.
    *
    * @param gen - a by-name parameter that should, whenever evaluated, return a unique Aggregate Id
    */
  def withGeneratedId(gen: => A#Id): AggregateConfigWithManagedId[A] = {
    val strategy = new GeneratedIdStrategy[A] {
      def generateId(): Id = gen
    }
    AggregateConfigWithManagedId(name, behavior, strategy)
  }

  /**
    * Configure Aggregate to use a fixed Id.
    *
    * A [[SingletonIdStrategy]] will be constructed using the passed Id
    * @param uniqueId - the fixed Id to be used for this Aggregate
    */
  def withSingletonId(uniqueId: A#Id): AggregateConfigWithManagedId[A] = {
    val strategy = new SingletonIdStrategy[A] {
      val id: Id = uniqueId
    }
    AggregateConfigWithManagedId(name, behavior, strategy)
  }

}

case class AggregateConfigWithAssignedId[A <: AggregateLike](name: Option[String],
                                                             behavior: (A#Id) => Behavior[A],
                                                             idStrategy: AggregateIdStrategy[A]) extends AggregateConfig[A] {

  def withName(name: String): AggregateConfigWithAssignedId[A] =
    this.copy(name = Option(name))

}

case class AggregateConfigWithManagedId[A <: AggregateLike](name: Option[String],
                                                            behavior: (A#Id) => Behavior[A],
                                                            idStrategy: AggregateIdStrategy[A]) extends AggregateConfig[A] {

  def withName(name: String): AggregateConfigWithManagedId[A] =
    this.copy(name = Option(name))

}