package services.request

import models.{Action, Role}
import org.ada.server.AdaException
import org.ada.server.models.User
import reactivemongo.bson.BSONObjectID


class ActionPermissionService {

  def checkUserAllowed(user: Option[User], validRoles: Set[Role.Value], userIdsMapping: Map[Role.Value, Traversable[BSONObjectID]]) = {
    user match {
      case Some(currentUser) => {
        validRoles.map( role =>
          checkIsIncluded(currentUser._id.get, userIdsMapping.get(role).get)
        ).filter( isIncluded => isIncluded).size == 1
      }
      case None => throw new AdaException("No logged user found")
    }
  }


  def checkIsIncluded(userId: BSONObjectID, ids: Traversable[BSONObjectID]): Boolean = {
    ids.find(user => user == userId) match {
      case None => false
      case _ => true
    }
  }
}


