package io.funcqrs

case class Tag(key: String, value: String)

object Tags {

  def aggregateTag(value: String) = Tag("aggregateType", value)

  def dependentViews(value: String) = Tag("dependentViews", value)
}
