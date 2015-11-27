package persistence

import play.api.libs.iteratee.{ Concurrent, Enumerator }
import play.api.libs.json.JsObject
import play.modules.reactivemongo.json.collection.JSONBatchCommands.JSONCountCommand.Count
import reactivemongo.api.indexes.{ IndexType, Index }
import reactivemongo.bson.BSONObjectID

import scala.concurrent.Future

/**
 * Generic async repo trait
 * @param E type of entity
 * @param ID type of identity of entity (primary key)
 */
trait AsyncReadonlyRepo[E, ID] {
  def get(id: ID): Future[Option[E]]

  def find(
    criteria: Option[JsObject] = None,
    orderBy: Option[JsObject] = None,
    projection : Option[JsObject] = None,
    limit: Option[Int] = None,
    page: Option[Int] = None
  ): Future[Traversable[E]]

  def count(criteria: Option[JsObject]) : Future[Int]
}

trait AsyncRepo[E, ID] extends AsyncReadonlyRepo[E, ID] {
  def save(entity: E): Future[Either[String, ID]]
}

trait CrudRepo[E, ID] extends AsyncRepo[E, ID] {
  def update(entity: E): Future[Either[String, ID]]
  def delete(id: ID): Future[Either[String, ID]]
  def deleteAll : Future[String]
}

trait StreamRepo[E, ID] extends AsyncRepo[E, ID] {
  def stream: Enumerator[E]
}