package persistence

import models.Criterion
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.JsObject

import scala.concurrent.Future

/**
 * Generic async repo trait
  *
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
    * @param sort Column used as reference for sorting. Leave at None to use default.
    * @param projection Defines which columns are supposed to be returned. Leave at None to use default.
    * @param limit Page limit. Use to define chunk sizes. Leave at None to use default.
    * @param page Page to be returned. Specifies which chunk is returned.
    * @return Traversable object for iteration.
    */
  def find(
    criteria: Seq[Criterion[Any]] = Nil,
    sort: Seq[Sort] = Nil,
    projection: Traversable[String] = Nil,
    limit: Option[Int] = None,
    page: Option[Int] = None
  ): Future[Traversable[E]]

  /**
    * Return the number of elements matching criteria.
    *
    * @param criteria Filtering criteria object. Use a String to filter according to value of reference column. Use None for no filtering.
    * @return Number of maching elements.
    */
  def count(criteria: Seq[Criterion[Any]] = Nil): Future[Int]
}

trait AsyncRepo[E, ID] extends AsyncReadonlyRepo[E, ID] {

  import play.api.libs.concurrent.Execution.Implicits.defaultContext

  def save(entity: E): Future[ID]
  def save(entities: Traversable[E]): Future[Traversable[ID]] =
    Future.sequence(entities.map(save))
}

trait AsyncCrudRepo[E, ID] extends AsyncRepo[E, ID] {
  def update(entity: E): Future[ID]
  def delete(id: ID): Future[Unit]
  def deleteAll: Future[Unit]
}

trait AsyncStreamRepo[E, ID] extends AsyncRepo[E, ID] {
  def stream: Enumerator[E]
}