package controllers.sampleRequest

import akka.stream.Materializer
import be.objectify.deadbolt.scala.AuthenticatedRequest
import javax.inject.Inject
import javax.ws.rs.BadRequestException
import org.ada.web.controllers.BSONObjectIDStringFormatter
import org.ada.web.controllers.core.AdaBaseController
import org.ada.web.controllers.dataset.DataSetWebContext
import org.incal.core.FilterCondition
import org.incal.play.controllers.WebContext
import org.incal.play.security.AuthAction
import reactivemongo.bson.BSONObjectID
import services.SampleRequestService

import scala.concurrent.ExecutionContext.Implicits.global

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
    dataSetId: String,
    tableColumnNames: Seq[String],
    filter: Seq[FilterCondition],
    selectedIds: Seq[BSONObjectID]
  ) = AuthAction { implicit  request =>
    for {
      user <- getUserForRequest()
      csv <- sampleRequestService.createCsv(dataSetId, filter, tableColumnNames, selectedIds)
      _ <- sampleRequestService.sendToRems(csv, catalogueItemId, user)
    } yield {
      Ok("")
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

  private def getUserForRequest()(implicit request: AuthenticatedRequest[_]) =
    for {
      deadBoltUserOption <- currentUser()
      deadBoltUser = deadBoltUserOption.getOrElse(throw new BadRequestException("Request has no user associated with it."))
    } yield deadBoltUser.user

}
