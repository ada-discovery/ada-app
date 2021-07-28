package services

import akka.stream.scaladsl.Source
import akka.util.ByteString
import be.objectify.deadbolt.scala.AuthenticatedRequest
import com.google.inject.Inject
import models.sampleRequest.{OrganisationRepresentation, RequestFileRepresentation, RequestFileType, RequestRepresentation, RequestType}
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.ada.server.dataaccess.dataset.FilterRepoExtra._
import org.ada.server.field.FieldUtil
import org.ada.server.models.DataSetFormattersAndIds.{FieldIdentity, JsObjectIdentity}
import org.ada.server.models.{DataSetSetting, DataSpaceMetaInfo, FieldTypeId, User}
import org.ada.server.{AdaException, AdaParseException}
import org.ada.web.controllers.dataset.{DataSetViewHelper, TableViewData}
import org.ada.web.services.DataSpaceService
import org.ada.web.services.oidc.AccessResourceService
import org.incal.core.FilterCondition
import org.incal.core.dataaccess.Criterion
import org.incal.core.dataaccess.Criterion._
import org.incal.play.{Page, PageOrder}
import play.api.Configuration
import play.api.libs.json.{JsError, JsNull, JsSuccess, JsValue, Json}
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.Results
import reactivemongo.bson.BSONObjectID
import services.BatchOrderRequestRepoTypes.SampleRequestSettingRepo

import javax.ws.rs.core.HttpHeaders
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

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
 * Service providing functionality to submit requests to Podium
 */
class SampleRequestService @Inject() (
  dsaf: DataSetAccessorFactory,
  sampleRequestSettingRepo: SampleRequestSettingRepo,
  config: Configuration,
  ws: WSClient,
  val dataSpaceService: DataSpaceService,
  accessResourceService: AccessResourceService
) extends DataSetViewHelper {


  private val podiumApiUrl = config.getString("podium.apiUrl").getOrElse(
    throw new AdaException("Configuration issue: 'podium.apiUrl' was not found in the configuration file.")
  )

  /**
   * Create a valid CSV which can be attached to an REMS application
   *
   * @param dataSetId The data set id to use
   * @param conditions The filter conditions. If empty no filter is applied
   * @param fieldNames The field/column names to use. If empty all fields are selected
   * @param selectedIds The selected row ids to use. If empty all are selected
   * @return A string representing a valid CSV file.
   */
  def createCsvByOrganisation(
    user: User,
    dataSetId: String,
    conditions: Seq[FilterCondition],
    fieldNames: Seq[String],
    selectedIds: Seq[BSONObjectID]
  ): Future[List[(OrganisationRepresentation, String)]] = {
    val SEPARATOR = "\t"
    val fieldCriteria = if (fieldNames.nonEmpty) Vector(FieldIdentity.name #-> fieldNames) else Nil
    val selectCriteria = if (selectedIds.nonEmpty) Vector(JsObjectIdentity.name #-> selectedIds) else Nil
    for {
      dsa <- dsaf.getOrError(dataSetId)
      sampleRequestSetting <- sampleRequestSettingRepo.find(Seq("dataSetId" #== dataSetId))
      fields <- dsa.fieldRepo.find(fieldCriteria)
      valueCriteria <- FieldUtil.toDataSetCriteria(dsa.fieldRepo, conditions)
      items <- dsa.dataSetRepo.find(valueCriteria ++ selectCriteria)
      organisations <- getOrganisations(user)
    } yield {
      val organisationIdentifier =
        if(sampleRequestSetting.nonEmpty)
          sampleRequestSetting.head.organisationFieldIdName
        else throw new AdaException(s"Not found sample request settings for dataset '$dataSetId'")

      val header = fields.map(_.name)
      val itemsByOrg = items.groupBy(item => organisations((item \ organisationIdentifier).as[Long]))
      itemsByOrg.map(itemOrg => {
        val csv = new StringBuilder("")
        csv ++= header.mkString(SEPARATOR)
        csv += '\n'
        itemOrg._2 foreach { item =>
          val row = fields map { field => {
            val res = (item \ field.name).toOption
            if (res.isDefined && field.fieldTypeSpec.fieldType.compare(FieldTypeId.Enum) == 0) {
              field.fieldTypeSpec.enumValues(res.get.as[Int])
            } else
              res.getOrElse(JsNull).toString
            }
          }
          csv ++= row.mkString(SEPARATOR)
          csv += '\n'
        }
        (itemOrg._1, csv.toString())
      }).toList
    }
  }

  def sendToPodium(csvByOrg: List[(OrganisationRepresentation, String)],
                   user: User): Future[List[String]] = {
    Future.sequence(csvByOrg.map(csvOrg => sendRequest(csvOrg._1, csvOrg._2, user)))
  }

  private def sendRequest(orgRep: OrganisationRepresentation,
                          csv: String,
                          user: User): Future[String] = {
    for {
      draft <- createDraft(user)
      updatedDraft <- updateDraft(user, draft, orgRep)
      attachmentInfo <- addAttachment(user, updatedDraft, csv)
      _ <- setAttachmentType(user, attachmentInfo)
      submittedDraft <- submitDraft(user, updatedDraft)
    } yield {
      s"/#/requests/detail/${submittedDraft.uuid}"
    }
  }


  private def createDraft(user: User): Future[RequestRepresentation] = {

    def createPodiumDraft(authHeader: String) = {
      ws.url(s"$podiumApiUrl/api/requests/drafts")
        .withHeaders(HttpHeaders.AUTHORIZATION -> authHeader)
        .post(Results.EmptyContent())
    }

    for {
      res <- accessResourceService.accessResource(user, createPodiumDraft)
    } yield
      parseRequestRepresentation(res)
  }

  private def updateDraft(user: User,
                  requestRep: RequestRepresentation,
                  organisationRep: OrganisationRepresentation): Future[RequestRepresentation] = {


    def createDummyRequestRepresentation(requestRep: RequestRepresentation,
                                         orgRep: OrganisationRepresentation): RequestRepresentation = {

      val principalInvestigatorUpdated = requestRep.requestDetail.principalInvestigator
        .copy(name = Option("AutomaticGenName"),
          email = Option("AutomaticGenEmail@uni.lu"),
          jobTitle = Option("Doctor"),
          affiliation = Option("AutomaticGenAffiliation"))

      val requestDetailUpdated = requestRep.requestDetail
        .copy(title = Option("AutomaticGenRequest"),
          background = Option("AutomaticGenBackground"),
          researchQuestion = Option("AutomaticGenQuestion"),
          hypothesis = Option("AutomaticGenHypthesis"),
          methods = Option("AutomaticGenMethods"),
          relatedRequestNumber = Option("AutomaticGenRequestNumber"),
          principalInvestigator = principalInvestigatorUpdated,
          requestType =Option(Seq(RequestType.Material)),
          searchQuery = Option("AutomaticGenSearchQuery")
        )

      requestRep.copy(organisations = List(orgRep), requestDetail = requestDetailUpdated)

    }

    def updatePodiumDraft(authHeader: String, body: JsValue) = {
      ws.url(s"$podiumApiUrl/api/requests/drafts")
        .withHeaders(HttpHeaders.AUTHORIZATION -> authHeader)
        .put(body)
    }

    val dummyRequestRep = createDummyRequestRepresentation(requestRep, organisationRep)

    for {
      res <- accessResourceService.accessResource(user, Json.toJson(dummyRequestRep), updatePodiumDraft)
    } yield
      parseRequestRepresentation(res)
  }


  private def submitDraft(user: User,
                  updatedDraftRep: RequestRepresentation): Future[RequestRepresentation] = {

    def submitPodiumDraft(authHeader: String, body: String = "", requestRep: RequestRepresentation) = {
      ws.url(s"$podiumApiUrl/api/requests/drafts/${requestRep.uuid}/submit")
        .withHeaders(HttpHeaders.AUTHORIZATION -> authHeader)
        .get()
    }

    for {
      res <- accessResourceService.accessResource(
        user = user,
        requestBody = null,
        additionalParam = updatedDraftRep,
        callResource = submitPodiumDraft
      )
    } yield {
      res.json.validate[List[RequestRepresentation]] match {
        case r: JsSuccess[List[RequestRepresentation]] => r.get.head
        case e: JsError => throw AdaParseException("Error parsing RequestRepresentation Json", new Throwable(JsError.toJson(e).toString()))
      }
    }
  }

  private def addAttachment(user: User,
                            requestRep: RequestRepresentation, csv: String): Future[RequestFileRepresentation] = {

    def addAttachmentToPodiumDraft(authHeader: String, csv: String, requestRep: RequestRepresentation) = {
      ws.url(s"$podiumApiUrl/api/requests/${requestRep.uuid}/files")
        .withHeaders(HttpHeaders.AUTHORIZATION -> authHeader).post(
        Source(
          Vector(
            FilePart("file", "samples.csv", Option("text/csv"), Source(Vector(ByteString(csv))))
          )
        )
      )
    }

    for {
      res <- accessResourceService.accessResource(user, csv, requestRep, addAttachmentToPodiumDraft)
    } yield {
      parseRequestFileRepresentation(res)
    }

  }

  private def setAttachmentType(user: User, requestFileRep: RequestFileRepresentation): Future[RequestFileRepresentation] = {

    def setPodiumFileType(authHeader: String, body: JsValue, requestFileRep: RequestFileRepresentation) = {
      ws.url(s"$podiumApiUrl/api/requests/${requestFileRep.request.uuid}/files/${requestFileRep.uuid}/type")
        .withHeaders(HttpHeaders.AUTHORIZATION -> authHeader)
        .put(body)
    }

    val requestFileUpdated = requestFileRep.copy(requestFileType = RequestFileType.OTHER)

    for {
      res <- accessResourceService.accessResource(user, Json.toJson(requestFileUpdated), requestFileUpdated, setPodiumFileType)
    } yield
      parseRequestFileRepresentation(res)
  }



  private def parseRequestFileRepresentation(response: WSResponse): RequestFileRepresentation = {
    response.json.validate[RequestFileRepresentation] match {
      case r: JsSuccess[RequestFileRepresentation] => r.get
      case e: JsError => throw AdaParseException("Error parsing RequestFileRepresentation Json", new Throwable(JsError.toJson(e).toString()))
    }
  }

  private def parseRequestRepresentation(response: WSResponse): RequestRepresentation = {
    response.json.validate[RequestRepresentation] match {
      case r: JsSuccess[RequestRepresentation] => r.get
      case e: JsError => throw AdaParseException("Error parsing RequestRepresentation Json", new Throwable(JsError.toJson(e).toString()))
    }
  }


  /**
    * Get available organisation in podium
    * @param user
    * @return
    */
  private def getOrganisations(user: User): Future[Map[Long, OrganisationRepresentation]] = {

    def getPodiumOrganisations(authHeader: String) = {
      ws.url(s"$podiumApiUrl/api/organisations/available")
        .withHeaders(HttpHeaders.AUTHORIZATION -> authHeader)
        .get()
    }

    for {
      res <- accessResourceService.accessResource(user, getPodiumOrganisations)
    } yield {
      res.json.validate[List[OrganisationRepresentation]] match {
        case s: JsSuccess[List[OrganisationRepresentation]] => s.get.map(org => org.id.get -> org).toMap
        case e: JsError => throw AdaParseException("Error parsing OrganisationRepresentation Json", new Throwable(JsError.toJson(e).toString()))
      }
    }
  }


  /**
   * Build object containing all data needed to render the sample request submission form
   *
   * @param dataSetId The data set id used
   * @param request The current request
   * @return An ActionFormViewData object
   */
  def getActionFormViewData(
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

  private def toCriteria(
    filter: Seq[FilterCondition]
  ): Future[Seq[Criterion[Any]]] = {
    val fieldNames = filter.seq.map(_.fieldName)
    filterValueConverters(fieldNames).map(
      FilterCondition.toCriteria(_, filter)
    )
  }

  private def filterValueConverters(
    fieldNames: Traversable[String]
  ): Future[Map[String, String => Option[Any]]] =
    Future(Map())



}
