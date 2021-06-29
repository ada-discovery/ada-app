package services

import akka.stream.scaladsl.Source
import akka.util.ByteString
import be.objectify.deadbolt.scala.AuthenticatedRequest
import com.google.inject.Inject
import models.sampleRequest.OrganisationRepresentation
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.ada.server.dataaccess.dataset.FilterRepoExtra._
import org.ada.server.field.FieldUtil
import org.ada.server.models.DataSetFormattersAndIds.{FieldIdentity, JsObjectIdentity}
import org.ada.server.models.{DataSetSetting, DataSpaceMetaInfo, User}
import org.ada.server.{AdaException, AdaParseException}
import org.ada.web.controllers.dataset.{DataSetViewHelper, TableViewData}
import org.ada.web.models.JwtTokenInfo
import org.ada.web.services.DataSpaceService
import org.ada.web.services.oidc.BearerTokenService
import org.incal.core.FilterCondition
import org.incal.core.dataaccess.Criterion
import org.incal.core.dataaccess.Criterion._
import org.incal.play.{Page, PageOrder}
import play.api.Configuration
import play.api.cache.CacheApi
import play.api.libs.json.{JsError, JsNull, JsSuccess, Json}
import play.api.libs.ws.WSClient
import play.api.mvc.MultipartFormData.FilePart
import play.cache.NamedCache
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
 * A REMS catalogue item
 *
 * @param id The id of the item
 * @param name The readable name of the item
 * @param formId The id associated with the catalogue item
 */
case class CatalogueItem(
  id: Int,
  name: String,
  formId: Int
)

/**
 * Service providing functionality to submit requests to REMS
 */
class SampleRequestService @Inject() (
  dsaf: DataSetAccessorFactory,
  sampleRequestSettingRepo: SampleRequestSettingRepo,
  config: Configuration,
  ws: WSClient,
  val dataSpaceService: DataSpaceService,
  bearerTokenService: BearerTokenService,
  @NamedCache("jwt-user-cache") jwtUserCache: CacheApi
) extends DataSetViewHelper {

  private val remsUrl = config.getString("rems.url").getOrElse(
    throw new AdaException("Configuration issue: 'rems.url' was not found in the configuration file.")
  )
  private val remsServiceUser = config.getString("rems.serviceUser").getOrElse(
    throw new AdaException("Configuration issue: 'rems.serviceUser' was not found in the configuration file.")
  )
  private val remsUserPrefix = config.getString("rems.userPrefix").getOrElse(
    throw new AdaException("Configuration issue: 'rems.userPrefix' was not found in the configuration file.")
  )
  private val remsMasterApiKey = config.getString("rems.masterApiKey").getOrElse(
    throw new AdaException("Configuration issue: 'rems.masterApiKey' was not found in the configuration file.")
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
  def createCsv(
    dataSetId: String,
    conditions: Seq[FilterCondition],
    fieldNames: Seq[String],
    selectedIds: Seq[BSONObjectID]
  ): Future[String] = {
    val SEPARATOR = "\t"
    val dsa = dsaf(dataSetId).getOrElse(throw new IllegalArgumentException(s"Dataset '$dataSetId' does not exist."))
    val fieldCriteria = if (fieldNames.nonEmpty) Vector(FieldIdentity.name #-> fieldNames) else Nil
    val selectCriteria = if (selectedIds.nonEmpty) Vector(JsObjectIdentity.name #-> selectedIds) else Nil
    for {
      fields <- dsa.fieldRepo.find(fieldCriteria)
      valueCriteria <- FieldUtil.toDataSetCriteria(dsa.fieldRepo, conditions)
      items <- dsa.dataSetRepo.find(valueCriteria ++ selectCriteria)
    } yield {
      val header = fields.map(_.name)
      val csv = new StringBuilder("")
      csv ++= header.mkString(SEPARATOR)
      csv += '\n'
      items foreach { item =>
        val row = header map { header =>
          (item \ header).getOrElse(JsNull).toString
        }
        csv ++= row.mkString(SEPARATOR)
        csv += '\n'
      }
      csv.toString
    }
  }

  /**
   * Creates an application in REMS
   *
   * @param csv A String representing a valid CSV file used as an application attachment
   * @param catalogueItemId A REMS catalogue item to create an application for
   * @param catalogueFormId The REMS form id used by the catalogue item
   * @param user The Ada user who makes the request
   * @return The URL pointing to the newly created application
   */
  def sendToRems(
    csv: String,
    catalogueItemId: Int,
    catalogueFormId: Int,
    user: User
  ): Future[String] = {
    for {
      applicationId <- createApplication(catalogueItemId, user)
      attachmentId <- addAttachment(applicationId, csv, user)
      _ <- saveDraft(applicationId, attachmentId, catalogueFormId, user)
    } yield {
      s"$remsUrl/application/$applicationId"
    }
  }

  /**
   * Get available REMS catalogue items
   *
   * @return A sequence of catalogue items
   */
  def getCatalogueItems: Future[Seq[CatalogueItem]] = {

    for {
      bearerToken <- bearerTokenService.getBearerToken
      res <-
    } yield {
        if (res.status != 200) throw new AdaException("Failed to retrieve organisation list. Reason: " + res.body)
        res.json.validate[List[OrganisationRepresentation]] match {
          case s: JsSuccess[List[OrganisationRepresentation]] => s.get.map(o => CatalogueItem(1, o.shortName, 2))
          case e: JsError => throw AdaParseException("Error parsing bearer token", new Throwable(JsError.toJson(e).toString()))
        }
      }

    def getOrganisations(authHeader: String) = {
      ws.url("http://localhost:8080/api/organisations/available")
        .withHeaders(HttpHeaders.AUTHORIZATION -> authHeader)
        .get()
    }


    //Future(Seq(CatalogueItem(1, "title1", 1), CatalogueItem(2, "title2", 2)))
  }
    /*for {
      res <- ws.url(remsUrl + "/api/catalogue").withHeaders(
        "x-rems-user-id" -> remsServiceUser,
        "x-rems-api-key" -> remsMasterApiKey
      ).get()
    } yield {
      if (res.status != 200) throw new AdaException("Failed to retrieve catalogue items from REMS. Reason: " + res.body)
      res.json.as[Seq[JsObject]] map { catalogueItemJson =>
        CatalogueItem(
          (catalogueItemJson \ "id").as[Int],
          (catalogueItemJson \ "localizations" \ "en" \ "title").as[String],
          (catalogueItemJson \ "formid").as[Int]
        )
      }
    }*/

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
    val dsa = dsaf(dataSetId).getOrElse(throw new IllegalArgumentException(s"Dataset with id '$dataSetId' not found."))
    for {
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

  private def createApplication(catalogueItemId: Int, user: User): Future[Int] =
    for {
      res <- ws.url(remsUrl + "/api/applications/create").withHeaders(
        "x-rems-user-id" -> userToRemsUser(user),
        "x-rems-api-key" -> remsMasterApiKey,
        "Content-Type" -> "application/json"
      ).post(
        Json.obj("catalogue-item-ids" -> Vector(catalogueItemId))
      )
    } yield {
      if (res.status != 200) throw new AdaException("Could not create application in REMS. Reason: " + res.body)
      (res.json \ "application-id").as[Int]
    }

  private def addAttachment(applicationId: Int, csv: String, user: User): Future[Int] =
    for {
      res <- ws.url(remsUrl + "/api/applications/add-attachment").withQueryString(
        "application-id" -> applicationId.toString
      ).withHeaders(
        "x-rems-user-id" -> userToRemsUser(user),
        "x-rems-api-key" -> remsMasterApiKey,
        "Content-Type" -> "multipart/form-data; boundary=---------------------------244194806337621270012523953493"
      ).post(
        Source(
          Vector(
            FilePart("file", "samples.csv", Option("text/csv"), Source(Vector(ByteString(csv))))
          )
        )
      )
    } yield {
      if (res.status != 200) throw new AdaException("Could not add attachment in REMS. Reason: " + res.body)
      (res.json \ "id").as[Int]
    }

  private def saveDraft(applicationId: Int, attachmentId: Int, catalogueFormId: Int, user: User): Future[Unit] =
    for {
      res <- ws.url(remsUrl + "/api/applications/save-draft").withHeaders(
        "x-rems-user-id" -> userToRemsUser(user),
        "x-rems-api-key" -> remsMasterApiKey,
        "Content-Type" -> "application/json"
      ).post(
        Json.obj(
          "application-id" -> applicationId,
          "field-values" -> Json.arr(
            Json.obj(
              "form" -> catalogueFormId,
              "field" -> "fld1", // TODO: Lookup correct fieldId based on configurable field name
              "value" -> attachmentId.toString
            )
          )
        )
      )
    } yield {
      if (res.status != 200) throw new AdaException("Could not create application in REMS. Reason: " + res.body)
    }

  private def userToRemsUser(user: User) =
    remsUserPrefix + user.email.split("@").head

}
