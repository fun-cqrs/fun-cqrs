package io.funcqrs.backend

import io.funcqrs.Tag

sealed trait Query
final case class QueryByTag(tag: Tag) extends Query
final case class QueryByTags(tags: Set[Tag]) extends Query
case object QuerySelectAll extends Query

object QueryByTags {
  def apply(tags: Tag*): QueryByTags = QueryByTags(tags.toSet)
}
