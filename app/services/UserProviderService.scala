package services

import javax.inject.Inject
import org.ada.server.dataaccess.RepoTypes.UserRepo
import org.ada.server.models.User.UserIdentity
import reactivemongo.bson.BSONObjectID
import scala.concurrent.ExecutionContext.Implicits.global
import org.incal.core.dataaccess.Criterion.Infix

class UserProviderService  @Inject()(userRepo: UserRepo) {
  def getUsersByIds(userIds: Traversable[Option[BSONObjectID]]) = {
    userRepo.find(Seq(UserIdentity.name #-> userIds.toSeq)).map { users =>
      users.map(c => (c._id.get, c)).toMap
    }
  }

  def getUsers() = {
    userRepo.find().map { users =>
      users.map(c => (c._id.get, c)).toMap
    }
  }
}
