package controllers.requests

import javax.inject.Inject
import org.incal.play.controllers._
import org.incal.play.security.SecurityUtil.restrictAdminAnyNoCaching
import play.api.Logger
import reactivemongo.bson.BSONObjectID
import services.BatchOrderRequestRepoTypes.BatchOrderRequestRepo

import scala.concurrent.ExecutionContext.Implicits.global

@Deprecated
class RequestsController @Inject()(
    requestsRepo:BatchOrderRequestRepo
  ) extends BaseController {

  private val logger = Logger

  def getItemsRequest(requestId:BSONObjectID) = restrictAdminAnyNoCaching(deadbolt) {
    implicit request =>
      for {
        requestRead <- requestsRepo.get(requestId)
      } yield {
        Ok(views.html.requests.edit(requestRead))
      }
    }

}