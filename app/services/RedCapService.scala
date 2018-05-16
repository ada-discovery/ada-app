package services

import javax.inject.{Inject, Singleton}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.fasterxml.jackson.core.JsonParseException
import com.google.inject.assistedinject.Assisted
import play.api.libs.json.{JsArray, JsObject}
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}
import play.api.Configuration
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import dataaccess.JsonUtil._
import models.redcap.JsonFormat._
import models.redcap._
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import play.api.libs.ws.ahc.AhcWSClient
import scala.concurrent.duration._

import scala.concurrent.{Await, Future}

trait RedCapServiceFactory {
  def apply(@Assisted("url") url: String, @Assisted("token") token: String): RedCapService
}

trait RedCapService {

  /**
    * Retrieve all fields matching the filtering criterion and order them according to a reference field.
    *
    * @return Sorted records matching filter criterion.
    */
  def listAllRecords: Future[Seq[JsObject]]

  def listEventRecords(events: Seq[String]): Future[Seq[JsObject]]

  /**
    * Retrieve all metadata fields matching the filtering criterion and order them according to a reference field.
    *
    * @return Sorted records matching filter criterion.
    */
  def listMetadatas: Future[Seq[Metadata]]

  /**
    * Create list of all field names. Field names are sorted and filtered if they don't match filter criterion.
    *
    * @return Filtered and sorted list of records.
    */
  def listExportFields: Future[Seq[ExportField]]

  /**
    * Count all records in reference field.
    *
    * @return The number of records matching the filter string.
    */
  def countRecords : Future[Int]

  /**
    * Get the name of the field specified by given id string.
    *
    * @param id String for matching with reference field.
    * @return Json representation of matching record.
    */
  def getRecord(id: String) : Future[Seq[JsObject]]

  /**
    * Retrieve the metadata matching the filter.
    *
    * @param id
    * @return
    */
  def getMetadata(id: String) : Future[Seq[JsObject]]

  /**
    * Get the name of the field(s) specified by given id string.
    *
    * @param id String for matching with reference field.
    * @return Sequence of field(s) matching id.
    */
  def getExportField(id: String) : Future[Seq[JsObject]]
}

protected[services] class RedCapServiceWSImpl @Inject() (
    @Assisted("url") private val url: String,
    @Assisted("token") private val token: String,
    ws: WSClient,
    configuration: Configuration
  ) extends RedCapService {

  private val timeout = configuration.getLong("redcap.request.timeout").get
  private val createUnsecuredClient = configuration.getBoolean("redcap.create_unsecured_client").getOrElse(false)

  private val wsClient: WSClient =
    if (createUnsecuredClient) {
      implicit val system = ActorSystem()
      implicit val materializer = ActorMaterializer()

      val config = new DefaultAsyncHttpClientConfig.Builder()
      config.setAcceptAnyCertificate(true)
      config.setFollowRedirect(true)
      config.setReadTimeout(timeout.toInt)
      // DefaultAsyncHttpClient(config.build
      AhcWSClient(config.build)

//      val config = new AsyncHttpClientConfigBean()
//      config.setAcceptAnyCertificate(true)
//      config.setFollowRedirect(true)
//      config.setReadTimeout(timeout.toInt)
//      new NingWSClient(config)
    } else
      ws

  private val req: WSRequest = wsClient.url(url).withRequestTimeout(timeout millis)

  private val baseRequestData = Map(
    "token" -> token,
    "format" -> "json"
  )

  private def recordRequest(events: Seq[String] = Nil) = {
    val eventsParam = if (events.nonEmpty) Map("events" -> events.mkString(",")) else Nil

    baseRequestData ++ Map("content" -> "record", "type" -> "flat") ++ eventsParam
  }


  private val metadataRequestData = baseRequestData ++ Map("content" -> "metadata")
  private val fieldNamesRequestData = baseRequestData ++ Map("content" -> "exportFieldNames")

  // Services

  override def listAllRecords =
    runRedCapQuery(recordRequest())


  override def listEventRecords(events: Seq[String]) =
    runRedCapQuery(recordRequest(events))

  override def listMetadatas =
    runRedCapQuery(metadataRequestData).map(
      _.map(_.as[Metadata])
    )

  override def listExportFields =
    runRedCapQuery(fieldNamesRequestData).map(
      _.map(_.as[ExportField])
    )

  override def countRecords =
    runRedCapQuery(recordRequest()).map( items =>
      count(items, "", "")
    )

  override def getRecord(id: String) =
    runRedCapQuery(recordRequest()).map { items =>
      findBy(items, id, "cdisc_dm_usubjd")}

  override def getMetadata(id: String) =
    runRedCapQuery(metadataRequestData).map { items =>
      findBy(items, id, "field_name")}

  override def getExportField(id: String) =
    runRedCapQuery(fieldNamesRequestData).map { items =>
      findBy(items, id, "export_field_name")}

  // Helper methods

  private def runRedCapQuery(requestData : Map[String, String]) =
    req.post(requestData.map { case (a, b) => (a, Seq(b)) }).map { response =>
      try {
        handleErrorResponse(response)
        response.json.as[JsArray].value.asInstanceOf[Seq[JsObject]]
      } catch {
        case e: JsonParseException => {
          throw new AdaRestException("Couldn't parse Red Cap JSON response.")
        }
      }
    }

  private def handleErrorResponse(response: WSResponse): Unit =
    response.status match {
      case x if x >= 200 && x<= 299 => ()
      case 401 | 403 => throw new AdaUnauthorizedAccessRestException(response.status + ": Unauthorized access.")
      case _ => throw new AdaRestException(response.status + ": " + response.statusText + "; " + response.body)
    }
}