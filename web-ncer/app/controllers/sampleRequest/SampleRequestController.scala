package controllers.sampleRequest

import akka.stream.Materializer
import be.objectify.deadbolt.scala.AuthenticatedRequest
import javax.inject.Inject
import org.ada.server.models.User
import org.ada.web.controllers.BSONObjectIDStringFormatter
import org.ada.web.controllers.core.AdaBaseController
import org.ada.web.controllers.dataset.DataSetWebContext
import org.incal.core.FilterCondition
import org.incal.play.controllers.WebContext
import org.incal.play.security.AuthAction
import play.api.mvc.AnyContent
import reactivemongo.bson.BSONObjectID
import services.SampleRequestService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class SampleRequest(
  dataSetId: String,
  itemIds: Seq[BSONObjectID]
)

class SampleRequestController @Inject()(
  sampleRequestService: SampleRequestService
)(
  implicit materializer: Materializer
) extends AdaBaseController {

  private implicit val idsFormatter = BSONObjectIDStringFormatter
  private def dataSetWebContext(dataSetId: String)(implicit context: WebContext) = DataSetWebContext(dataSetId)

  def submitRequest(
    catalogueItemId: Int,
    catalogueFormId: Int,
    dataSetId: String,
    tableColumnNames: Seq[String],
    filter: Seq[FilterCondition],
    selectedIds: Seq[BSONObjectID]
  ) = AuthAction { implicit  request =>
    val itemId = if (catalogueItemId == -1) {
      lookupKeyValueInRequestBody("catalogueItemId", request)
    } else
      catalogueItemId

    val formId = if (catalogueFormId == -1) {
      lookupKeyValueInRequestBody("catalogueFormId", request)
    } else
      catalogueFormId

    for {
      user <- getUserForRequest()
      csv <- sampleRequestService.createCsv(dataSetId, filter, tableColumnNames, selectedIds)
      url <- sampleRequestService.sendToRems(csv, itemId, formId, user)
    } yield {
      PermanentRedirect(url)
    }
  }

  def submissionForm(dataSet: String) = AuthAction { implicit request =>
    implicit val dataSetWebCtx = dataSetWebContext(dataSet)
    for {
      formViewData <- sampleRequestService.getActionFormViewData(dataSet)
      catalogueItems <- sampleRequestService.getCatalogueItems
    } yield Ok(views.html.sampleRequest.submissionForm(
        catalogueItems,
        formViewData.dataViewId,
        formViewData.tableViewParts,
        formViewData.dataSetSetting,
        formViewData.dataSpaceMetaInfos
    ))
  }

  private def lookupKeyValueInRequestBody(key: String, request: AuthenticatedRequest[AnyContent]): Int = {
    val exc = new IllegalArgumentException(s"'$key' not specified in URL or body.")
    request.body
      .asFormUrlEncoded
      .getOrElse(throw exc)
      .find({ case (k, _) => k == key })
      .getOrElse(throw exc)
      ._2
      .head
      .toInt
  }

  private def getUserForRequest()(implicit request: AuthenticatedRequest[_]) =
    for {
      deadBoltUserOption <- currentUser()
      deadBoltUser = deadBoltUserOption.getOrElse(throw new BadRequestException("Request has no user associated with it."))
    } yield deadBoltUser.user

}
