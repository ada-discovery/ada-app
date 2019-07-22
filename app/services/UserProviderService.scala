package services

import controllers.requests.routes
import javax.inject.Inject
import models.{BatchOrderRequest, Role}
import org.ada.server.dataaccess.RepoTypes.UserRepo
import org.ada.server.models.User.UserIdentity
import play.api.mvc.RequestHeader
import reactivemongo.bson.BSONObjectID
import services.BatchOrderRequestRepoTypes.BatchOrderRequestRepo

import scala.concurrent.ExecutionContext.Implicits.global
import org.incal.core.dataaccess.Criterion.Infix


class UserProviderService  @Inject()(userRepo: UserRepo) {


  def getUsersByIds(userIds: Traversable[Option[BSONObjectID]]) = {
//    val userIds = requests.map(_.createdById).flatten.map(Some(_)).toSeq

   // users <- userRepo.find(Seq(UserIdentity.name #-> userIds.map(id => Some(id))))

    userRepo.find(Seq(UserIdentity.name #-> userIds.toSeq)).map { users =>
      users.map(c => (c._id.get, c)).toMap
    }
  }


}
