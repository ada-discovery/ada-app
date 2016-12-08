package dataaccess

import com.google.inject.ImplementedBy
import dataaccess.RepoTypes.{UserRepo, FilterRepo}
import dataaccess.User.UserIdentity
import dataaccess.ignite.FilterCacheCrudRepoFactory
import scala.concurrent.Future
import models.Filter
import dataaccess.Criterion.Infix
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