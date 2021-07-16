package controllers.sampleRequest

import akka.stream.Materializer
import be.objectify.deadbolt.scala.AuthenticatedRequest
import org.ada.server.AdaException
import org.ada.server.models.Filter.filterConditionFormat
import org.ada.web.controllers.BSONObjectIDStringFormatter
import org.ada.web.controllers.core.AdaBaseController
import org.ada.web.controllers.dataset.DataSetWebContext
import org.incal.core.FilterCondition
import org.incal.play.controllers.WebContext
import org.incal.play.formatters.JsonFormatter
import play.api.Configuration
import play.api.data.Form
import play.api.data.Forms._
import reactivemongo.bson.BSONObjectID
import services.SampleRequestService

import javax.inject.Inject
import javax.ws.rs.BadRequestException
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class SampleRequest(
  dataSetId: String,
  itemIds: Seq[BSONObjectID]
)

/**
 * Controller that facilitates communication with REMS
 */
class SampleRequestController @Inject()(
  config: Configuration,
  sampleRequestService: SampleRequestService
)(
  implicit materializer: Materializer
) extends AdaBaseController {

  private implicit val idsFormatter = BSONObjectIDStringFormatter
  private def dataSetWebContext(dataSetId: String)(implicit context: WebContext) = DataSetWebContext(dataSetId)
  private implicit val filterConditionFormatter = JsonFormatter[FilterCondition]
  private def sampleRequestPermission(dataSet: String) = s"DS:$dataSet.dataSet"

  private val podiumFrontUrl = config.getString("podium.frontUrl").getOrElse(
    throw new AdaException("Configuration issue: 'podium.frontUrl' was not found in the configuration file.")
  )

  /**
   * Information needed to build a data frame for submission to REMS
   *
   * @param dataSetId The ID of the data set
   * @param tableColumnNames The field names that will be submitted
   * @param catalogueItemId The ID of a REMS catalogue item that will be target of the submission
   * @param catalogueFormId The ID of the REMS form to use
   * @param selectedIds The data set row IDs selected for submission. If empty, all are selected.
   * @param conditions The present filter conditions. If none are present, no filters are applied.
   */
  case class SampleRequest(
    dataSetId: String,
    tableColumnNames: Seq[String],
    selectedIds: Seq[BSONObjectID],
    conditions: Seq[FilterCondition]
  )

  val requestForm = Form(
    mapping(
      "dataSetId" -> nonEmptyText,
      "tableColumnNames" -> seq(nonEmptyText),
      "selectedIds" -> seq(of[BSONObjectID]),
      "conditions" -> seq(of[FilterCondition])
    )(SampleRequest.apply)(SampleRequest.unapply)
  )

  /**
   * Triggers request submission to REMS
   * @param dataSetId Selected data set
   * @return REMS URL of created sample request application
   */
  def submitRequest(dataSetId: String) =
    restrictAdminOrPermissionAny(sampleRequestPermission(dataSetId)) { implicit  request =>
      requestForm.bindFromRequest.fold(
        formWithError => {
          Future(BadRequest("Form submission failed. Please contact administrator."))
        },
        sampleRequest => {
          for {
            user <- getUserForRequest()
            csvByOrg <- sampleRequestService.createCsvByOrganisation(
              user,
              dataSetId,
              sampleRequest.conditions,
              sampleRequest.tableColumnNames,
              sampleRequest.selectedIds
            )
            urls <- sampleRequestService.sendToPodium(
              csvByOrg,
              user
            )
          } yield
            Ok(if(urls.size == 1) s"$podiumFrontUrl${urls.head}"
               else s"$podiumFrontUrl/#/requests/my-requests")
        }
      )
    }

  /**
   * Renders the form for user interaction
   * @param dataSet Selected data set
   * @return The form
   */
  def submissionForm(dataSet: String) =
    restrictAdminOrPermissionAny(sampleRequestPermission(dataSet)) { implicit request =>
      implicit val dataSetWebCtx = dataSetWebContext(dataSet)
      for {
        formViewData <- sampleRequestService.getActionFormViewData(dataSet)
      } yield Ok(views.html.sampleRequest.submissionForm(
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
      deadBoltUser = deadBoltUserOption.getOrElse(
        throw new BadRequestException("Request has no user associated with it.")
      )
    } yield deadBoltUser.user

}
