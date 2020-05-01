package org.ada.server.dataaccess

import javax.inject.Inject
import org.ada.server.AdaException
import org.ada.server.dataaccess.RepoTypes.{InputRunnableSpecRepo, RunnableSpecRepo}
import org.ada.server.models.{BaseRunnableSpec, InputRunnableSpec, RunnableSpec}
import org.incal.core.dataaccess.{AsyncCrudRepo, Criterion, Sort}
import reactivemongo.bson.BSONObjectID

import scala.concurrent.Future

private final class RunnableSpecCrudRepo extends AsyncCrudRepo[BaseRunnableSpec, BSONObjectID] {

  @Inject private var runnableSpecRepo: RunnableSpecRepo = _
  @Inject private var inputRunnableSpecRepo: InputRunnableSpecRepo = _

  override def find(
    criteria: Seq[Criterion[Any]],
    sort: Seq[Sort],
    projection: Traversable[String],
    limit: Option[Int],
    skip: Option[Int]
  ): Future[Traversable[BaseRunnableSpec]] =
    runnableSpecRepo.find(criteria, sort, projection, limit, skip)

  override def count(criteria: Seq[Criterion[Any]]): Future[Int] =
    runnableSpecRepo.count(criteria)

  // TODO: optimize this
  override def get(id: BSONObjectID): Future[Option[BaseRunnableSpec]] =
    try {
      inputRunnableSpecRepo.get(id)
    } catch {
      case e: AdaException =>
        println((e))
        runnableSpecRepo.get(id)
    }

  override def save(entity: BaseRunnableSpec): Future[BSONObjectID] =
    entity match {
      case x: RunnableSpec => runnableSpecRepo.save(x)
      case x: InputRunnableSpec[_] => inputRunnableSpecRepo.save(x)
    }

  override def update(entity: BaseRunnableSpec): Future[BSONObjectID] =
    entity match {
      case x: RunnableSpec => runnableSpecRepo.update(x)
      case x: InputRunnableSpec[_] => inputRunnableSpecRepo.update(x)
    }

  override def delete(id: BSONObjectID): Future[Unit] =
    runnableSpecRepo.delete(id)

  override def deleteAll: Future[Unit] =
    runnableSpecRepo.deleteAll

  override def flushOps: Future[Unit] =
    runnableSpecRepo.flushOps
}
