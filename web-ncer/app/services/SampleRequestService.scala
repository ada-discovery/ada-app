package services

import com.google.inject.Inject
import org.ada.server.AdaException
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.ada.server.field.FieldUtil
import org.ada.server.models.DataSetFormattersAndIds.FieldIdentity
import org.incal.core.FilterCondition
import org.incal.core.dataaccess.Criterion._
import org.ada.server.field.FieldUtil.{FieldOps, JsonFieldOps}
import play.api.Configuration
import play.api.libs.json.{JsObject, Json}
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try

trait SampleRequestService {
  def createCsv(
    dataSetId: String,
    filter: Seq[FilterCondition] = Nil,
    columNames: Seq[String] = Nil
  ): Future[Any]

  def sendToRems(
    csv: Any,
    catalogueItemId: Int
  ): Unit
}

class SampleRequestServiceImpl @Inject() (
  dsaf: DataSetAccessorFactory,
  config: Configuration,
  ws: WSClient
) extends SampleRequestService {

  private val remsUrl = config.getString("rems.host").getOrElse(
    throw new AdaException("Configuration issue: 'rems.host' was not found in the configuration file.")
  )
  private val remsUser = config.getString("rems.user").getOrElse(
    throw new AdaException("Configuration issue: 'rems.user' was not found in the configuration file.")
  )
  private val remsApiKey = config.getString("rems.apiKey").getOrElse(
    throw new AdaException("Configuration issue: 'rems.apiKey' was not found in the configuration file.")
  )

  override def createCsv(
    dataSetId: String,
    filter: Seq[FilterCondition] = Nil,
    fieldNames: Seq[String] = Nil
  ): Future[Any] = {
    val dsa = dsaf(dataSetId).getOrElse(throw new IllegalArgumentException(s"Dataset '$dataSetId' does not exist."))
    val fieldRepo = dsa.fieldRepo
    val dataSetRepo = dsa.dataSetRepo

    for {
      fields <- fieldRepo.find(Vector(FieldIdentity.name #-> fieldNames))
      criteria <- FieldUtil.toDataSetCriteria(fieldRepo, filter)
      items <- dataSetRepo.find(criteria)
    } yield {
      val fieldNameTypes = fields.map(_.toNamedTypeAny).toSeq
      val fieldNameValues = fieldNameTypes map { fieldNameType =>
        val values = items.map(_.toValue(fieldNameType)).toSeq
        (fieldNameType._1, values)
      } toMap
      // TODO: return suitable format
    }
  }

  override def sendToRems(
    csv: Any,
    catalogueItemId: Int
  ): Unit = {
    for {
      applicationId <- createApplication(catalogueItemId)
    } yield {

    }
  }

  private def createApplication(catalogueItemId: Int): Future[Int] =
    for {
      res <- ws.url(remsUrl + "/api/applications/create").withHeaders(
      "x-rems-user-id" -> remsUser,
      "x-rems-api-key" -> remsApiKey,
      "Content-Type" -> "application/json"
      ).withBody(
        Json.obj("catalogue-item-ids" -> Vector(catalogueItemId))
      ).get()
    } yield {
      if (res.status != 200) throw new AdaException("Could not create application in REMS. Reason: " + res.body)
      (res.json \ "application-id").as[Int]
    }

}