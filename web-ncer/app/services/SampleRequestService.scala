package services

import java.nio.charset.StandardCharsets

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.google.inject.{ImplementedBy, Inject}
import org.ada.server.AdaException
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.ada.server.field.FieldUtil
import org.ada.server.field.FieldUtil.{FieldOps, JsonFieldOps}
import org.ada.server.models.DataSetFormattersAndIds.FieldIdentity
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import org.incal.core.FilterCondition
import org.incal.core.dataaccess.Criterion._
import play.api.Configuration
import play.api.libs.json.{JsObject, Json}
import play.api.libs.ws.WSClient
import play.api.libs.ws.ahc.AhcWSClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@ImplementedBy(classOf[SampleRequestServiceImpl])
trait SampleRequestService {
  def createCsv(
    dataSetId: String,
    filter: Seq[FilterCondition] = Nil,
    columNames: Seq[String] = Nil
  ): Future[Any]

  def sendToRems(
    csv: String,
    catalogueItemId: Int
  ): Future[_]

  def getCatalogueItems: Future[Map[String, Int]]
}

class SampleRequestServiceImpl @Inject() (
  dsaf: DataSetAccessorFactory,
  config: Configuration,
  ws: WSClient
) extends SampleRequestService {

  private val remsUrl = config.getString("rems.url").getOrElse(
    throw new AdaException("Configuration issue: 'rems.url' was not found in the configuration file.")
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
    val fieldCriteria = if (fieldNames.nonEmpty) Vector(FieldIdentity.name #-> fieldNames) else Nil
    for {
      fields <- fieldRepo.find(fieldCriteria)
      valueCriteria <- FieldUtil.toDataSetCriteria(fieldRepo, filter)
      items <- dataSetRepo.find(valueCriteria)
    } yield {
      val fieldNameTypes = fields.map(_.toNamedTypeAny).toSeq
      val fieldNameValues: Map[String, Seq[Option[Any]]] = fieldNameTypes map { fieldNameType =>
        val values = items.map(_.toValue(fieldNameType)).toSeq
        (fieldNameType._1, values)
      } toMap
      val header = fieldNameValues.keySet.toVector
      val csv = new StringBuilder("")
      csv ++= header.mkString("\t")
      csv += '\n'

    }
  }

  override def sendToRems(
    csv: String,
    catalogueItemId: Int
  ): Future[_] = {
    for {
      applicationId <- createApplication(catalogueItemId)
      res <- ws.url(remsUrl + "/api/applications/add-attachment").withQueryString(
        "application-id" -> applicationId.toString
      ).withHeaders(
        "x-rems-user-id" -> remsUser,
        "x-rems-api-key" -> remsApiKey
      ).put(
        csv.getBytes(StandardCharsets.UTF_8)
      )
    } yield {
      res.status
    }
  }

  override def getCatalogueItems: Future[Map[String, Int]] =
    for {
      res <- ws.url(remsUrl + "/api/catalogue").withHeaders(
      "x-rems-user-id" -> remsUser,
      "x-rems-api-key" -> remsApiKey
      ).get()
    } yield {
      if (res.status != 200) throw new AdaException("Failed to retrieve catalogue items from REMS. Reason: " + res.body)
      res.json.as[Seq[JsObject]] map { catalogueItemJson =>
        (catalogueItemJson \ "resource-name").as[String] -> (catalogueItemJson \ "id").as[Int]
      } toMap
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

}