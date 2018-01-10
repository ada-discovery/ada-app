package dataaccess

import akka.stream.scaladsl.Source
import play.api.libs.iteratee.Enumerator
import reactivemongo.akkastream.State

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Generic async repo trait
  *
  * @param E type of entity
 * @param ID type of identity of entity (primary key)
 */
trait AsyncReadonlyRepo[+E, ID] {
  def get(id: ID): Future[Option[E]]

  /**
    * Gets all elements matching criteria object.
    * Returned values are chunked according to the arguments for pagination.
    *
    * @param criteria Filtering criteria object. Use a String to filter according to value of reference column. Use None for no filtering.
    * @param sort Column used as reference for sorting. Leave at None to use default.
    * @param projection Defines which columns are supposed to be returned. Leave at None to use default.
    * @param limit Page limit. Use to define chunk sizes. Leave at None to use default.
    * @param skip The number of items to skip.
    * @return Traversable object for iteration.
    */
  def find(
    criteria: Seq[Criterion[Any]] = Nil,
    sort: Seq[Sort] = Nil,
    projection: Traversable[String] = Nil,
    limit: Option[Int] = None,
    skip: Option[Int] = None
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

  def save(entity: E): Future[ID]
  def save(entities: Traversable[E]): Future[Traversable[ID]] =
    Future.sequence(entities.map(save))
  def flushOps: Future[Unit] =
    throw new AdaDataAccessException("Flush not supported.")
}

trait AsyncCrudRepo[E, ID] extends AsyncRepo[E, ID] {
  def update(entity: E): Future[ID]
  def update(entities: Traversable[E]): Future[Traversable[ID]] =
    Future.sequence(entities.map(update))
  def delete(id: ID): Future[Unit]
  def delete(ids: Traversable[ID]): Future[Unit]=
    Future.sequence(ids.map(delete)).map(_ -> ())
  def deleteAll: Future[Unit]
}

trait AsyncStreamRepo[E, ID] extends AsyncRepo[E, ID] {
  def stream: Source[E, Future[State]]
  @Deprecated
  def oldStream: Enumerator[E]
}