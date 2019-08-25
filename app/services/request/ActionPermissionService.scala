package services.request

import models.{Action, Role}
import org.ada.server.AdaException
import org.ada.server.models.User
import reactivemongo.bson.BSONObjectID

// TODO: Why "class" if no attributes?? Normally (to learn Scala a bit) you should use trait, which gives you more flexibility and is less heavy, although in this case I would remove this class completely
class ActionPermissionService {

  // TODO: this doesn't nothing else just calls a function with the ~same params
  def checkUserHasRoles(user: Option[User], assumingRole: Option[Role.Value], validRoles: Set[Role.Value], userIdsMapping: Map[Role.Value, Traversable[BSONObjectID]]) =
    user match {
      case Some(currentUser) => roleMatchesWithRequestPermission(user.get, assumingRole, validRoles, userIdsMapping)
      case None => throw new AdaException("No logged user found")
    }

  def assumedRoleMatchesUserRole(user: User, userIdsMapping: Traversable[BSONObjectID]) =
    userIdsMapping.toSeq.contains(user._id.get)

  def roleMatchesWithRequestPermission(
    user: User,
    assumingRole: Option[Role.Value],
    validRoles: Set[Role.Value],
    userIdsMapping: Map[Role.Value, Traversable[BSONObjectID]]
  ): Boolean = {
    assumingRole match {
      case Some(someRole) => assumedRoleMatchesUserRole(user, userIdsMapping.get(someRole).get) && validRoles.filter(role => someRole == role).size == 1
      case None =>  userIdsMapping.values.filter(ids=>ids.toSet.contains(user._id.get)).size >= 1
    }
  }
}