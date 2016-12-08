package dataaccess

import com.google.inject.ImplementedBy
import dataaccess.RepoTypes._
import dataaccess.User.UserIdentity
import dataaccess.ignite.DataViewCacheCrudRepoFactory
import models.DataView
import dataaccess.Criterion.Infix
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

@ImplementedBy(classOf[DataViewCacheCrudRepoFactory])
trait DataViewRepoFactory {
  def apply(dataSetId: String): DataViewRepo
}

object DataViewRepo {

  def setCreatedBy(
    userRepo: UserRepo,
    dataViews: Traversable[DataView]
  ): Future[Unit] = {
    val userIds = dataViews.map(_.createdById).flatten.map(Some(_)).toSeq

    if (userIds.nonEmpty) {
      userRepo.find(Seq(UserIdentity.name #-> userIds)).map { users =>
        val userIdMap = users.map(c => (c._id.get, c)).toMap
        dataViews.foreach(dataView =>
          if (dataView.createdById.isDefined) {
            dataView.createdBy = userIdMap.get(dataView.createdById.get)
          }
        )
      }
    } else
      Future(())
  }
}