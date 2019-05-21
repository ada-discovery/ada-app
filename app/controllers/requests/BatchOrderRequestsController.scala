package controllers.requests

import java.util.Date

import javax.inject.Inject
import models.{BatchOrderRequest, BatchRequestState}
import org.ada.server.models.Translation
import org.ada.web.controllers.core.AdaCrudControllerImpl
import org.ada.web.controllers.routes
import org.incal.play.controllers._
import org.incal.play.security.SecurityUtil.restrictAdminAnyNoCaching
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms.{ignored, mapping}
import reactivemongo.bson.BSONObjectID
import services.BatchOrderRequestRepoTypes.BatchOrderRequestRepo
import play.api.data.Form
import play.api.data.Forms.{date, ignored, mapping, nonEmptyText}
import play.api.data.Forms.set
import BatchOrderRequest.batchRequestFormat
import com.banda.network.domain.ActivationFunctionType
import org.incal.play.formatters.{EnumFormatter, JavaEnumFormatter, SeqFormatter}
import org.incal.spark_ml.models.VectorScalerType
import play.api.data.Forms.{mapping, _}
import play.api.libs.json.Json
import views.html.{translation => view}

import scala.concurrent.ExecutionContext.Implicits.global

@Deprecated
class BatchOrderRequestsController @Inject()(
    requestsRepo:BatchOrderRequestRepo
  ) extends AdaCrudControllerImpl[BatchOrderRequest, BSONObjectID](requestsRepo)
  with AdminRestrictedCrudController[BSONObjectID]
  with HasBasicFormCrudViews[BatchOrderRequest, BSONObjectID] {

  private implicit val requiestStateFormatter = EnumFormatter(BatchRequestState)

  override protected[controllers] val form = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "dataSetId" -> nonEmptyText,
      "itemIds" -> nonEmptyText,
      "state"-> of[BatchRequestState.Value],
      "created by" -> ignored(Option.empty[BSONObjectID]),
  "date" -> ignored(new Date())
    )(BatchOrderRequest.apply)(BatchOrderRequest.unapply))


  override protected val homeCall = routes.BatchOrderRequestsController.find()
  override protected def createView = { implicit ctx => views.html.requests.create(_) }
  override protected def showView = editView
  override protected def editView = { implicit ctx => views.html.requests.edit(_) }
  override protected def listView = { implicit ctx => (views.html.requests.list(_, _)).tupled }
}