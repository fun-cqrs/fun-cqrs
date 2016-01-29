package io.funcqrs.backend

import io.funcqrs.Tag

trait Query
case class QueryByTag(tag: Tag) extends Query
case class QueryByTags(tags: Set[Tag]) extends Query

object QueryByTags {
  def apply(tags: Tag*): QueryByTags = QueryByTags(tags.toSet)
}