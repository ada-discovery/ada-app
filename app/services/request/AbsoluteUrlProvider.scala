package services.request

import controllers.requests.routes
import models.Role
import play.api.mvc.RequestHeader
import reactivemongo.bson.BSONObjectID

// TODO: Remove
class AbsoluteUrlProvider {

  def getReadOnlyUrl(requestId: BSONObjectID)(implicit request: RequestHeader) =
    routes.BatchOrderRequestsController.get(requestId).absoluteURL()

  def getActionUrl(requestId: BSONObjectID, role: Role.Value)(implicit request: RequestHeader) =
    routes.BatchOrderRequestsController.action(requestId, role).absoluteURL()
}
