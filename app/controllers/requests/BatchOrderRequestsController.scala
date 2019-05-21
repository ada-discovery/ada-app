package controllers.requests

import java.util.Date

import javax.inject.Inject
import models.BatchOrderRequest.batchRequestFormat
import models.{BatchOrderRequest, BatchRequestState}
import org.ada.server.AdaException
import org.ada.server.models.{DataSpaceMetaInfo, DataView, User}
import org.ada.server.services.UserManager
import org.ada.web.controllers.DataSetControllerActionNames
import org.ada.web.controllers.core.AdaCrudControllerImpl
import org.ada.web.security.AdaAuthConfig
import org.incal.play.controllers._
import org.incal.play.formatters.EnumFormatter
import play.api.data.Form
import play.api.data.Forms.{ignored, mapping, nonEmptyText, _}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc.{AnyContent, Request}
import reactivemongo.bson.BSONObjectID
import services.BatchOrderRequestRepoTypes.BatchOrderRequestRepo
import reactivemongo.bson.BSONObjectID
import scala.concurrent.Future

@Deprecated
class BatchOrderRequestsController @Inject()(
                                              requestsRepo: BatchOrderRequestRepo,
                                              val userManager: UserManager
                                            ) extends AdaCrudControllerImpl[BatchOrderRequest, BSONObjectID](requestsRepo)
  with AdminRestrictedCrudController[BSONObjectID]
  with HasBasicFormCrudViews[BatchOrderRequest, BSONObjectID]
  with AdaAuthConfig {

  private implicit val requiestStateFormatter = EnumFormatter(BatchRequestState)

  override protected[controllers] val form = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "dataSetId" -> nonEmptyText,
      "itemIds" -> nonEmptyText,
      "state" -> of[BatchRequestState.Value],
      "created by" -> ignored(Option.empty[BSONObjectID]),
      "date" -> ignored(new Date())
    )(BatchOrderRequest.apply)(BatchOrderRequest.unapply))
  override protected val homeCall = routes.BatchOrderRequestsController.find()

  override def saveCall(
                         batchRequest: BatchOrderRequest)(
                         implicit request: Request[AnyContent]
                       ): Future[BSONObjectID] =
    for {
      user <- currentUser(request)
      id <- {
        val batchRequestWithUser = user match {
          case Some(user) =>
            batchRequest.copy(timeCreated = new Date(), createdById = user._id)
          case None => throw new AdaException("No logged user found")
        }
        repo.save(batchRequestWithUser)
      }
    } yield
      id

  override protected def createView = { implicit ctx => views.html.requests.create(_) }

  override protected def showView = editView

  override protected def editView = { implicit ctx => views.html.requests.edit(_) }

  override protected def listView = { implicit ctx => (views.html.requests.list(_, _)).tupled }
}