package controllers.sampleRequest

import akka.stream.Materializer
import be.objectify.deadbolt.scala.AuthenticatedRequest
import models.sampleRequest.SampleRequest
import org.ada.server.AdaException
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.ada.server.dataaccess.dataset.FilterRepoExtra._
import org.ada.server.models.{DataSetSetting, DataSpaceMetaInfo}
import org.ada.web.controllers.core.AdaBaseController
import org.ada.web.controllers.dataset.{DataSetViewHelper, DataSetWebContext, TableViewData}
import org.ada.web.services.DataSpaceService
import org.incal.core.FilterCondition
import org.incal.core.dataaccess.Criterion
import org.incal.core.dataaccess.Criterion._
import org.incal.play.controllers.WebContext
import org.incal.play.{Page, PageOrder}
import play.api.Configuration
import play.api.libs.json.{JsError, JsSuccess}
import reactivemongo.bson.BSONObjectID
import services.BatchOrderRequestRepoTypes.SampleRequestSettingRepo
import services.SampleRequestService

import javax.inject.Inject
import javax.ws.rs.BadRequestException
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


/**
 * Controller that facilitates communication with Podium
 */
class SampleRequestController @Inject()(
  config: Configuration,
  dsaf: DataSetAccessorFactory,
  sampleRequestSettingRepo: SampleRequestSettingRepo,
  val dataSpaceService: DataSpaceService,
  sampleRequestService: SampleRequestService
)(
  implicit materializer: Materializer
) extends AdaBaseController with DataSetViewHelper {

  private def dataSetWebContext(dataSetId: String)(implicit context: WebContext) = DataSetWebContext(dataSetId)
  private def sampleRequestPermission(dataSet: String) = s"DS:$dataSet.dataSet"

  private val podiumFrontUrl = config.getString("podium.frontUrl").getOrElse(
    throw new AdaException("Configuration issue: 'podium.frontUrl' was not found in the configuration file.")
  )

  /**
    * Triggers request submission to Podium
    * @param dataSetId Selected data set
    * @return Podium URL of created sample request application
    */
  def submitRequest(dataSetId: String) =
      restrictAdminOrPermissionAny(sampleRequestPermission(dataSetId)) { implicit request => {
        request.body.asJson.map { json =>
          json.validate[SampleRequest] match {
            case success: JsSuccess[SampleRequest] =>
              for {
                user <- getUserForRequest()
                csvByOrg <- sampleRequestService.createCsvByOrganisation(
                  user,
                  success.get
                )
                urls <- sampleRequestService.sendToPodium(
                  csvByOrg,
                  user
                )
              } yield
                Ok(if(urls.size == 1) s"$podiumFrontUrl${urls.head}"
                else s"$podiumFrontUrl/#/requests/my-requests")
            case error: JsError => Future(BadRequest(JsError.toJson(error)))
          }
        }.getOrElse(Future(BadRequest("JSON request expected")))
      }
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
        formViewData <- getActionFormViewData(dataSet)
      } yield Ok(views.html.sampleRequest.submissionRequest(
        formViewData.dataViewId,
        formViewData.tableViewParts,
        formViewData.dataSetSetting,
        formViewData.dataSpaceMetaInfos,
        formViewData.elementGridWidth
      ))
    }

  /**
    * Data needed for creating a view used for sample request submission
    *
    * @param dataViewId The id of an existing view to use as a base
    * @param tableViewParts Information of the data frame (table) to display
    * @param dataSetSetting Sample request settings associated with a dataset
    * @param dataSpaceMetaInfos Meta information
    * @param elementGridWidth Grid width to use for charts
    */
  case class ActionFormViewData(
  dataViewId: BSONObjectID,
  tableViewParts: Seq[TableViewData],
  dataSetSetting: DataSetSetting,
  dataSpaceMetaInfos: Traversable[DataSpaceMetaInfo],
  elementGridWidth: Int
  )
  /**
    * Build object containing all data needed to render the sample request submission form
    *
    * @param dataSetId The data set id used
    * @param request The current request
    * @return An ActionFormViewData object
    */
  private def getActionFormViewData(
                             dataSetId: String
                           )(
                             implicit request: AuthenticatedRequest[_]
                           ): Future[ActionFormViewData] = {
    for {
      dsa <- dsaf.getOrError(dataSetId)
      (dataSetName, dataSpaceTree, dataSetSetting) <- getDataSetNameTreeAndSetting(dsa)
      sampleRequestSettings <- sampleRequestSettingRepo.find(Seq("dataSetId" #== dataSetId))
      _ = require(sampleRequestSettings.nonEmpty,
        s"Sample Request settings have not been defined for dataset '$dataSetId'"
      )
      sampleRequestSetting = sampleRequestSettings.head
      dataViewOption <- dsa.dataViewRepo.get(sampleRequestSetting.viewId)
      dataView = dataViewOption.getOrElse(
        throw new IllegalArgumentException(s"ViewId specified in Sample Request setting for dataset '$dataSetId' does no longer exist.")
      )
      resolvedFilters <- Future.sequence(dataView.filterOrIds.map(dsa.filterRepo.resolve)).map { filters =>
        if (filters.isEmpty) Seq(org.ada.server.models.Filter()) else filters
      }
      conditions = resolvedFilters.map(_.conditions)
      criteria <- Future.sequence(conditions.map(toCriteria))
      tableColumnNames = dataView.tableColumnNames
      tablePagesToUse = Seq.fill(resolvedFilters.size)(PageOrder(0, ""))
      nameFieldMap <- createNameFieldMap(dsa.fieldRepo)(conditions, Nil, tableColumnNames)
      viewTableResponses <-
        Future.sequence(
          (tablePagesToUse, criteria, resolvedFilters).zipped.map { case (tablePage, criteria, resolvedFilter) =>
            getInitViewResponse(dsa.dataSetRepo)(
              tablePage.page, tablePage.orderBy, resolvedFilter, criteria, nameFieldMap, tableColumnNames, 20
            )
          }
        )
    } yield {
      val tableViewData = (viewTableResponses, tablePagesToUse).zipped.map {
        case (viewResponse, tablePage) =>
          val newPage = Page(viewResponse.tableItems, tablePage.page, tablePage.page * 20, viewResponse.count, tablePage.orderBy)
          TableViewData(newPage, Some(viewResponse.filter), viewResponse.tableFields)
      }

      ActionFormViewData(
        sampleRequestSetting.viewId,
        tableViewData,
        dataSetSetting,
        dataSpaceTree,
        dataView.elementGridWidth
      )
    }
  }

  private def toCriteria(filter: Seq[FilterCondition]): Future[Seq[Criterion[Any]]] = {
    val fieldNames = filter.seq.map(_.fieldName)
    filterValueConverters(fieldNames).map(
      FilterCondition.toCriteria(_, filter)
    )
  }

  private def filterValueConverters(fieldNames: Traversable[String]): Future[Map[String, String => Option[Any]]] =
    Future(Map())

  private def getUserForRequest()(implicit request: AuthenticatedRequest[_]) =
    for {
      deadBoltUserOption <- currentUser()
      deadBoltUser = deadBoltUserOption.getOrElse(
        throw new BadRequestException("Request has no user associated with it.")
      )
    } yield deadBoltUser.user

}
