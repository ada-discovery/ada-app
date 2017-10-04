package dataaccess

import com.google.inject.ImplementedBy
import dataaccess.RepoTypes.{FilterRepo, JsonCrudRepo, UserRepo}
import dataaccess.User.UserIdentity
import dataaccess.ignite.FilterCacheCrudRepoFactory

import scala.concurrent.Future
import models.Filter
import dataaccess.Criterion.Infix
import models.DataSetFormattersAndIds.JsObjectIdentity
import models.FilterCondition.FilterOrId
import play.api.libs.json.JsObject
import reactivemongo.bson.BSONObjectID

import scala.concurrent.ExecutionContext.Implicits.global

@ImplementedBy(classOf[FilterCacheCrudRepoFactory])
trait FilterRepoFactory {
  def apply(dataSetId: String): FilterRepo
}

object FilterRepo {

  def setCreatedBy(
    userRepo: UserRepo,
    filters: Traversable[Filter]
  ): Future[Unit] = {
    val userIds = filters.map(_.createdById).flatten.map(Some(_)).toSeq

    if (userIds.nonEmpty) {
      userRepo.find(Seq(UserIdentity.name #-> userIds)).map { users =>
        val userIdMap = users.map(c => (c._id.get, c)).toMap
        filters.foreach(filter =>
          if (filter.createdById.isDefined) {
            filter.createdBy = userIdMap.get(filter.createdById.get)
          }
        )
      }
    } else
      Future(())
  }
}

object FilterRepoExtra {

  implicit class InfixOps(val filterRepo: FilterRepo) extends AnyVal {

    def resolve(filterOrId: FilterOrId): Future[Filter] =
      // use a given filter conditions or load one
      filterOrId match {
        case Right(id) =>
          filterRepo.get(id).map(_.getOrElse(new Filter(Nil)))

        case Left(conditions) =>
          Future(new models.Filter(conditions))
      }
  }
}