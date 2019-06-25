package services.request

import controllers.requests.routes
import play.api.mvc.RequestHeader
import reactivemongo.bson.BSONObjectID

class AbsoluteUrlProvider {

  def getReadOnlyUrl(requestId: BSONObjectID)(implicit request: RequestHeader) ={
    routes.BatchOrderRequestsController.get(requestId).absoluteURL()
  }

  def getActionUrl(requestId: BSONObjectID)(implicit request: RequestHeader) ={
    routes.BatchOrderRequestsController.action(requestId).absoluteURL()
  }
}
