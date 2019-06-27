package services.request

import models.{Action, Role}
import org.ada.server.AdaException
import org.ada.server.models.User
import reactivemongo.bson.BSONObjectID


class ActionPermissionService {

  def checkUserAllowed(user: Option[User], assumingRole: Role.Value, validRoles: Set[Role.Value], userIdsMapping: Map[Role.Value, Traversable[BSONObjectID]]) = {
    user match {
      case Some(currentUser) => {
        assumedRoleMatchesUserRole(user.get, userIdsMapping.get(assumingRole).get) match {
          case true => {
            validRoles.filter(role => assumingRole == role).size == 1
          }
          case false => false
        }
      }
      case None => throw new AdaException("No logged user found")
    }
  }

  def assumedRoleMatchesUserRole(user: User, userIdsMapping: Traversable[BSONObjectID]) = {
    userIdsMapping.toSeq.contains(user._id.get)
  }
}


