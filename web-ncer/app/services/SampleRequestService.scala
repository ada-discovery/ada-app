package services

import java.nio.charset.StandardCharsets

import akka.stream.scaladsl
import akka.stream.scaladsl.Source
import be.objectify.deadbolt.scala.AuthenticatedRequest
import com.google.inject.Inject
import org.ada.server.AdaException
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.ada.server.field.FieldUtil
import org.ada.server.models.DataSetFormattersAndIds.{FieldIdentity, JsObjectIdentity}
import org.ada.server.models.{DataSetSetting, DataSpaceMetaInfo, Filter, User}
import org.ada.web.controllers.dataset.{DataSetViewHelper, TableViewData}
import org.ada.web.services.DataSpaceService
import org.incal.core.FilterCondition
import org.incal.core.dataaccess.Criterion._
import play.api.Configuration
import play.api.libs.json.{JsNull, JsObject, Json}
import play.api.libs.ws.WSClient
import play.api.mvc.MultipartFormData.{DataPart, FilePart, Part}
import reactivemongo.bson.BSONObjectID
import services.BatchOrderRequestRepoTypes.SampleRequestSettingRepo
import org.ada.server.dataaccess.dataset.FilterRepoExtra._
import org.incal.core.dataaccess.Criterion
import org.incal.play.{Page, PageOrder}
import akka.stream.scaladsl._
import akka.stream.scaladsl.FileIO
import akka.util.ByteString

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class ActionFormViewData(
  dataViewId: BSONObjectID,
  tableViewParts: Seq[TableViewData],
  dataSetSetting: DataSetSetting,
  dataSpaceMetaInfos: Traversable[DataSpaceMetaInfo]
)

class SampleRequestService @Inject() (
  dsaf: DataSetAccessorFactory,
  sampleRequestSettingRepo: SampleRequestSettingRepo,
  config: Configuration,
  ws: WSClient,
  val dataSpaceService: DataSpaceService
) extends DataSetViewHelper {

  private val remsUrl = config.getString("rems.url").getOrElse(
    throw new AdaException("Configuration issue: 'rems.url' was not found in the configuration file.")
  )
  private val remsUser = config.getString("rems.user").getOrElse(
    throw new AdaException("Configuration issue: 'rems.user' was not found in the configuration file.")
  )
  private val remsApiKey = config.getString("rems.apiKey").getOrElse(
    throw new AdaException("Configuration issue: 'rems.apiKey' was not found in the configuration file.")
  )

  def createCsv(
    dataSetId: String,
    filter: Seq[FilterCondition] = Nil,
    fieldNames: Seq[String] = Nil,
    selectedIds: Seq[BSONObjectID]
  ): Future[String] = {
    val SEPARATOR = "\t"
    val dsa = dsaf(dataSetId).getOrElse(throw new IllegalArgumentException(s"Dataset '$dataSetId' does not exist."))
    val fieldRepo = dsa.fieldRepo
    val dataSetRepo = dsa.dataSetRepo
    val fieldCriteria = if (fieldNames.nonEmpty) Vector(FieldIdentity.name #-> fieldNames) else Nil
    val selectCriteria = if (selectedIds.nonEmpty) Vector(JsObjectIdentity.name #-> selectedIds) else Nil
    for {
      fields <- fieldRepo.find(fieldCriteria)
      valueCriteria <- FieldUtil.toDataSetCriteria(fieldRepo, filter)
      items <- dataSetRepo.find(valueCriteria ++ selectCriteria)
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

  def sendToRems(
    csv: String,
    catalogueItemId: Int,
    user: User
  ): Future[_] = {
    for {
      applicationId <- createApplication(catalogueItemId)
      _ <- addAttachment(applicationId, csv)
      _ <- inviteMember(applicationId, user.ldapDn, user.email)
    } yield { }
  }

  def getCatalogueItems: Future[Map[String, Int]] =
    for {
      res <- ws.url(remsUrl + "/api/catalogue").withHeaders(
        "x-rems-user-id" -> remsUser,
        "x-rems-api-key" -> remsApiKey
      ).get()
    } yield {
      if (res.status != 200) throw new AdaException("Failed to retrieve catalogue items from REMS. Reason: " + res.body)
      res.json.as[Seq[JsObject]] map { catalogueItemJson =>
        (catalogueItemJson \ "localizations" \ "en" \ "title").as[String] -> (catalogueItemJson \ "id").as[Int]
      } toMap
    }
  
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

      ActionFormViewData(sampleRequestSetting.viewId, tableViewData, dataSetSetting, dataSpaceTree)
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


  private def addAttachment(applicationId: Int, csv: String): Future[Unit] =
    for {
      res <- ws.url(remsUrl + "/api/applications/add-attachment").withQueryString(
        "application-id" -> applicationId.toString
      ).withHeaders(
        "x-rems-user-id" -> remsUser,
        "x-rems-api-key" -> remsApiKey,
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
    }

  private def createApplication(catalogueItemId: Int): Future[Int] =
    for {
      res <- ws.url(remsUrl + "/api/applications/create").withHeaders(
        "x-rems-user-id" -> remsUser,
        "x-rems-api-key" -> remsApiKey,
        "Content-Type" -> "application/json"
      ).post(
        Json.obj("catalogue-item-ids" -> Vector(catalogueItemId))
      )
    } yield {
      if (res.status != 200) throw new AdaException("Could not create application in REMS. Reason: " + res.body)
      (res.json \ "application-id").as[Int]
    }

  private def inviteMember(applicationId: Int, name: String, email: String): Future[Unit] = {
    for {
      res <- ws.url(remsUrl + "/api/applications/invite-member").withHeaders(
        "x-rems-user-id" -> remsUser,
        "x-rems-api-key" -> remsApiKey,
        "Content-Type" -> "application/json"
      ).post(
        Json.obj(
          "application-id" -> applicationId,
          "member" -> Json.obj(
            "name" -> name,
            "email" -> email
          )
        )
      )
    } yield {
      if (res.status != 200) throw new AdaException("Could not invite member in REMS. Reason: " + res.body)
    }
  }
}