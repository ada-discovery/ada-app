package controllers.sampleRequest

import akka.stream.Materializer
import be.objectify.deadbolt.scala.AuthenticatedRequest
import javax.inject.Inject
import org.ada.server.models.Filter.filterConditionFormat
import org.ada.server.models.User
import org.ada.web.controllers.BSONObjectIDStringFormatter
import org.ada.web.controllers.core.AdaBaseController
import org.ada.web.controllers.dataset.DataSetWebContext
import org.incal.core.FilterCondition
import org.incal.play.controllers.WebContext
import org.incal.play.formatters.JsonFormatter
import org.incal.play.security.AuthAction
import play.api.data.Form
import play.api.data.Forms._
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
  private implicit val filterConditionFormatter = JsonFormatter[FilterCondition]


  case class SampleRequest(
    dataSetId: String,
    tableColumnNames: Seq[String],
    catalogueItemId: Int,
    catalogueFormId: Int,
    selectedIds: Seq[BSONObjectID],
    conditions: Seq[FilterCondition]
  )

  val requestForm = Form(
    mapping(
      "dataSetId" -> nonEmptyText,
      "tableColumnNames" -> seq(nonEmptyText),
      "catalogueItemId" -> number,
      "catalogueFormId" -> number,
      "selectedIds" -> seq(of[BSONObjectID]),
      "conditions" -> seq(of[FilterCondition])
    )(SampleRequest.apply)(SampleRequest.unapply)
  )

  def submitRequest = AuthAction { implicit  request =>
    requestForm.bindFromRequest.fold(
      formWithError => {
        Future(BadRequest("Form submission failed. Please contact administrator."))
      },
      sampleRequest => {
        for {
          user <- getUserForRequest()
          csv <- sampleRequestService.createCsv(
            sampleRequest.dataSetId,
            sampleRequest.conditions,
            sampleRequest.tableColumnNames,
            sampleRequest.selectedIds
          )
          url <- sampleRequestService.sendToRems(
            csv,
            sampleRequest.catalogueItemId,
            sampleRequest.catalogueFormId,
            user
          )
        } yield {
          Ok(url)
        }
      }
    )
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
      formViewData.dataSpaceMetaInfos,
      formViewData.elementGridWidth
    ))
  }

  private def getUserForRequest()(implicit request: AuthenticatedRequest[_]) =
    for {
      deadBoltUserOption <- currentUser()
      deadBoltUser = deadBoltUserOption.getOrElse(throw new BadRequestException("Request has no user associated with it."))
    } yield deadBoltUser.user

}
