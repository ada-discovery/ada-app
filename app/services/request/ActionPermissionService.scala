package services.request

import models.{Action, Role}
import org.ada.server.AdaException
import org.ada.server.models.User
import reactivemongo.bson.BSONObjectID


class ActionPermissionService {

  def checkUserAllowed(user: Option[User], assumingRole: Option[Role.Value], validRoles: Set[Role.Value], userIdsMapping: Map[Role.Value, Traversable[BSONObjectID]]) = {
    user match {
      case Some(currentUser) => roleMatchesWithRequestPermission(user.get,assumingRole, validRoles, userIdsMapping)
      case None => throw new AdaException("No logged user found")
    }
  }

  def assumedRoleMatchesUserRole(user: User, userIdsMapping: Traversable[BSONObjectID]) = {
    userIdsMapping.toSeq.contains(user._id.get)
  }


  def roleMatchesWithRequestPermission(user: User,assumingRole: Option[Role.Value], validRoles: Set[Role.Value],  userIdsMapping: Map[Role.Value, Traversable[BSONObjectID]] ): Boolean = {
    assumingRole match {
      case Some(role) => assumedRoleMatchesUserRole(user, userIdsMapping.get(role).get) && validRoles.filter(role => assumingRole == role).size == 1
      case None =>  userIdsMapping.values.filter(ids=>ids.toSet.contains(user._id.get)).size == 1
    }
  }
}


