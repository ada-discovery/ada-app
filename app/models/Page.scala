package models

import util.FilterSpec

/**
 * Helper for pagination.
 */
case class Page[A](items: Traversable[A], page: Int, offset: Long, total: Long, orderBy: String, filter: FilterSpec) {
  lazy val prev = Option(page - 1).filter(_ >= 0)
  lazy val next = Option(page + 1).filter(_ => (offset + items.size) < total)
}
