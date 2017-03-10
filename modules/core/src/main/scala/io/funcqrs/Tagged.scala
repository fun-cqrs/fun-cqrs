package io.funcqrs

trait Tagged {
  def tags: Set[Tag]
}
