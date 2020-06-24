package services

import java.nio.charset.StandardCharsets

import be.objectify.deadbolt.scala.AuthenticatedRequest
import com.google.inject.Inject
import org.ada.server.AdaException
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.ada.server.field.FieldUtil
import org.ada.server.models.DataSetFormattersAndIds.{FieldIdentity, JsObjectIdentity}
import org.ada.server.models.{DataSetSetting, DataSpaceMetaInfo, User}
import org.ada.web.controllers.dataset.{DataSetViewHelper, TableViewData}
import org.incal.core.FilterCondition
import org.incal.core.dataaccess.Criterion._
import play.api.Configuration
import play.api.libs.json.{JsNull, JsObject, Json}
import play.api.libs.ws.WSClient
import reactivemongo.bson.BSONObjectID
import services.BatchOrderRequestRepoTypes.SampleRequestSettingRepo

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
  ws: WSClient
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
      csv ++= header.mkString("\t")
      csv += '\n'
      items foreach { item =>
        val row = header map { header =>
          (item \ header).getOrElse(JsNull).toString
        }
        csv ++= row.mkString("\t")
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

    } yield ActionFormViewData(sampleRequestSetting.viewId, Vector(), dataSetSetting, dataSpaceTree)
  }

  private def addAttachment(applicationId: Int, csv: String): Future[Unit] =
    for {
      res <- ws.url(remsUrl + "/api/applications/add-attachment").withQueryString(
        "application-id" -> applicationId.toString
      ).withHeaders(
        "x-rems-user-id" -> remsUser,
        "x-rems-api-key" -> remsApiKey
      ).put(
        csv.getBytes(StandardCharsets.UTF_8)
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