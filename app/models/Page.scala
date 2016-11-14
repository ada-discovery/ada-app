package models

import reactivemongo.bson.BSONObjectID

/**
 * Helper for pagination.
 */
case class Page[A](
  items: Traversable[A],
  page: Int,
  offset: Long,
  total: Long,
  orderBy: String,
  filter: Option[Filter] = None
) {
  lazy val prev = Option(page - 1).filter(_ >= 0)
  lazy val next = Option(page + 1).filter(_ => (offset + items.size) < total)

  lazy val filterConditions: Seq[FilterCondition] =
    filter.map(_.conditions).getOrElse(Nil)

  lazy val filterId: Option[BSONObjectID] =
    filter.map(_._id).flatten
}
