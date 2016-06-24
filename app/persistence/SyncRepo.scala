package persistence

import java.util.concurrent.Future

import play.api.libs.iteratee.{ Enumerator }
import play.api.libs.json.JsObject

import scala.concurrent.{Future, Await, Awaitable}
import scala.concurrent.duration.Duration

/**
 * Generic sync repo trait
 * @param E type of entity
 * @param ID type of identity of entity (primary key)
 */
trait SyncReadonlyRepo[E, ID] {
  def get(id: ID): Option[E]

  def find(
    criteria: Option[JsObject] = None,
    orderBy: Option[Seq[Sort]] = None,
    projection : Option[JsObject] = None,
    limit: Option[Int] = None,
    page: Option[Int] = None
  ): Traversable[E]

  def count(criteria: Option[JsObject]) : Int
}

trait SyncRepo[E, ID] extends SyncReadonlyRepo[E, ID] {
  def save(entity: E): ID
  def save(entities: Traversable[E]): Traversable[ID] =
    entities.map(save)
}

trait SyncCrudRepo[E, ID] extends SyncRepo[E, ID] {
  def update(entity: E): ID
  def delete(id: ID)
  def deleteAll
}

trait SyncStreamRepo[E, ID] extends SyncRepo[E, ID] {
  def stream: Enumerator[E]
}

// Adapters

protected class SyncReadonlyRepoAdapter[E, ID](
    asyncRepo : AsyncReadonlyRepo[E, ID],
    timeout : Duration
  ) extends SyncReadonlyRepo[E, ID] {

  override def get(id: ID): Option[E] =
    wait(asyncRepo.get(id))

  override def find(
    criteria: Option[JsObject] = None,
    orderBy: Option[Seq[Sort]] = None,
    projection : Option[JsObject] = None,
    limit: Option[Int] = None,
    page: Option[Int] = None
  ) =
    wait(asyncRepo.find(criteria, orderBy, projection, limit, page))

  override def count(criteria: Option[JsObject]) =
    wait(asyncRepo.count(criteria))

  protected def wait[T](future : Awaitable[T]): T =
    Await.result(future, timeout)
}

private class SyncRepoAdapter[E, ID](
    asyncRepo : AsyncRepo[E, ID],
    timeout : Duration
  ) extends SyncReadonlyRepoAdapter[E, ID](asyncRepo, timeout) with SyncRepo[E, ID] {

  override def save(entity: E) =
    wait(asyncRepo.save(entity))
}

private class SyncCrudRepoAdapter[E, ID](
   asyncRepo : AsyncCrudRepo[E, ID],
   timeout : Duration
  ) extends SyncRepoAdapter[E, ID](asyncRepo, timeout) with SyncCrudRepo[E, ID] {

  override def update(entity: E) =
    wait(asyncRepo.update(entity))

  override def delete(id: ID) =
    wait(asyncRepo.delete(id))

  override def deleteAll  =
    wait(asyncRepo.deleteAll)
}

private class SyncStreamRepoAdapter[E, ID](
    asyncRepo : AsyncStreamRepo[E, ID],
    timeout : Duration
  ) extends SyncRepoAdapter[E, ID](asyncRepo, timeout) with SyncStreamRepo[E, ID] {

  override def stream = asyncRepo.stream
}

object RepoSynchronizer {

  def apply[E, ID](asyncRepo : AsyncReadonlyRepo[E, ID], timeout : Duration) : SyncReadonlyRepo[E, ID] =
    new SyncReadonlyRepoAdapter(asyncRepo, timeout)

  def apply[E, ID](asyncRepo : AsyncRepo[E, ID], timeout : Duration) : SyncRepo[E, ID] =
    new SyncRepoAdapter(asyncRepo, timeout)

  def apply[E, ID](asyncRepo : AsyncCrudRepo[E, ID], timeout : Duration) : SyncCrudRepo[E, ID] =
    new SyncCrudRepoAdapter(asyncRepo, timeout)

  def apply[E, ID](asyncRepo : AsyncStreamRepo[E, ID], timeout : Duration) : SyncStreamRepo[E, ID] =
    new SyncStreamRepoAdapter(asyncRepo, timeout)
}