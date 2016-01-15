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

  /**
    * Gets all elements matching criteria object.
    * Returned values are chunked according to the arguments for pagination.
    *
    * @param criteria Filtering criteria object. Use a String to filter according to value of reference column. Use None for no filtering.
    * @param orderBy Column used as reference for sorting. Leave at None to use default.
    * @param projection Defines which columns are supposed to be returned. Leave at None to use default.
    * @param limit Page limit. Use to define chunk sizes. Leave at None to use default.
    * @param page Page to be returned. Specifies which chunk is returned.
    * @return Traversable object for iteration.
    */
  def find(
    criteria: Option[JsObject] = None,
    orderBy: Option[JsObject] = None,
    projection : Option[JsObject] = None,
    limit: Option[Int] = None,
    page: Option[Int] = None
  ): Future[Traversable[E]]

  /**
    * Return the number of elements matching criteria.
    *
    * @param criteria Filtering criteria object. Use a String to filter according to value of reference column. Use None for no filtering.
    * @return Number of maching elements.
    */
  def count(
    criteria: Option[JsObject] = None
  ): Future[Int]

//  def getDictionary : models.Dictionary
}

trait AsyncRepo[E, ID] extends AsyncReadonlyRepo[E, ID] {
  def save(entity: E): Future[ID]
}

trait AsyncCrudRepo[E, ID] extends AsyncRepo[E, ID] {
  def update(entity: E): Future[ID]
  def updateCustom(id: ID, modifier : JsObject): Future[Unit]
  def delete(id: ID): Future[Unit]
  def deleteAll : Future[Unit]
}

trait AsyncStreamRepo[E, ID] extends AsyncRepo[E, ID] {
  def stream: Enumerator[E]
}