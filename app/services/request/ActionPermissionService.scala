package services.request

import java.util.Date

import models.{NotificationType, Role}
import org.ada.server.AdaException
import org.ada.server.models.User
import reactivemongo.bson.BSONObjectID

import scala.concurrent.Future


class ActionPermissionService {

   def checkUserAllowed(user: Option[User], action: models.Action, userIdsMapping: Map[Role.Value, Traversable[BSONObjectID]]) = {
    user match {
      case Some(currentUser) => {
        action.allowed match {
          case Role.Committee => checkIsIncluded(currentUser._id.get, userIdsMapping.get(Role.Committee).get)
          case Role.Owner => checkIsIncluded(currentUser._id.get, userIdsMapping.get(Role.Owner).get)
          case Role.Requester => checkIsIncluded(currentUser._id.get, userIdsMapping.get(Role.Requester).get)
        }
      }
      case None => throw new AdaException("No logged user found")
    }
  }


  def checkIsIncluded(userId : BSONObjectID ,ids : Traversable[BSONObjectID])={
    ids.find(user => user == userId) match {
      case None => throw new AdaException("user " + userId + " not found in required group, action denied")
      case _ =>
    }
  }
}


