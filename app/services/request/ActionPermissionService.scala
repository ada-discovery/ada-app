package services.request

import models.Role
import org.ada.server.AdaException
import reactivemongo.bson.BSONObjectID


class ActionPermissionService {

  def checkUserAllowed(action: models.Action, currentUserId: Option[BSONObjectID], createdById: Option[BSONObjectID], committee: Seq[BSONObjectID], owners: Seq[BSONObjectID]) = {

     action.allowed match {
      case Role.Committee => checkIsIncluded(currentUserId.get,committee)
      case Role.Owner => checkIsIncluded(currentUserId.get,owners)
      case Role.Requester => checkIsIncluded(currentUserId.get,Seq(createdById.get))
    }
  }


  def checkIsIncluded(userId : BSONObjectID ,ids : Seq[BSONObjectID])={
    ids.find(_=>true) match {
      case None => throw new AdaException("user " + userId + " not found in required group, action denied")
      case _ =>
    }
  }
}


