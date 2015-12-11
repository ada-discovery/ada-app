package persistence

import play.api.libs.iteratee.Enumerator
import play.api.libs.json.JsObject

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

trait AsyncCrudRepo[E, ID] extends AsyncRepo[E, ID] {
  def update(entity: E): Future[Either[String, ID]]
  def updateCustom(id: ID, modifier : JsObject): Future[Either[String, ID]]
  def delete(id: ID): Future[Either[String, ID]]
  def deleteAll : Future[String]
}

trait AsyncStreamRepo[E, ID] extends AsyncRepo[E, ID] {
  def stream: Enumerator[E]
}