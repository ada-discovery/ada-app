package services

import javax.inject.{Inject, Singleton}

import com.fasterxml.jackson.core.JsonParseException
import com.google.inject.assistedinject.Assisted
import play.api.libs.json.{JsObject, JsArray}
import play.api.libs.ws.{WSResponse, WSRequest, WSClient}
import play.api.Configuration
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import dataaccess.JsonUtil._

import models.redcap.JsonFormat._
import models.redcap._

import scala.concurrent.{Await, Future}

trait RedCapServiceFactory {
  def apply(@Assisted("url") url: String, @Assisted("token") token: String): RedCapService
}

object RedCapServiceFactory {
  def defaultRedCapService(redCapServiceFactory: RedCapServiceFactory, configuration: Configuration): RedCapService = {
    val url = configuration.getString("redcap.prodserver.api.url").get
    val token = configuration.getString("redcap.prodserver.token").get
    redCapServiceFactory(url, token)
  }
}
trait RedCapService {

  /**
    * Retrieve all fields matching the filtering criterion and order them according to a reference field.
    *
    * @param orderBy Reference field for ordering/ sorting.
    * @param filter Field for macthing and filtering the reference field.
    * @return Sorted records matching filter criterion.
    */
  def listRecords(orderBy: String, filter: String) : Future[Seq[JsObject]]

  /**
    * Retrieve all metadata fields matching the filtering criterion and order them according to a reference field.
    *
    * @param orderBy Reference field for ordering/ sorting.
    * @param filter Field for macthing and filtering the reference field.
    * @return Sorted records matching filter criterion.
    */
  def listMetadatas(orderBy: String, filter: String) : Future[Seq[Metadata]]

  /**
    * Create list of all field names. Field names are sorted and filtered if they don't match filter criterion.
    *
    * @param orderBy Reference field for sorting.
    * @param filter Specify filter to exclude fields from name list.
    * @return Filtered and sorted list of records.
    */
  def listExportFields(orderBy: String, filter: String) : Future[Seq[ExportField]]

  /**
    * Count all records in reference field.
    *
    * @param filter Value for filtering reference field.
    * @return The number of records matching the filter string.
    */
  def countRecords(filter: String) : Future[Int]

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

  private val req: WSRequest = ws.url(url).withRequestTimeout(360000) // configuration.getString("redcap.prodserver.api.url").get

  private val baseRequestData = Map(
    "token" -> token,   // configuration.getString("redcap.prodserver.token").get
    "format" -> "json"
  )

  private val recordRequestData = baseRequestData ++ Map("content" -> "record", "type" -> "flat")
  private val metadataRequestData = baseRequestData ++ Map("content" -> "metadata")
  private val fieldNamesRequestData = baseRequestData ++ Map("content" -> "exportFieldNames")

  private def jsonRecords = runRedCapQuery(recordRequestData)

  private def jsonMetadatas = runRedCapQuery(metadataRequestData)

  private def jsonFieldNames = runRedCapQuery(fieldNamesRequestData)

  // Services

  override def listRecords(orderBy: String, filter: String) =
    jsonRecords.map( items =>
      filterAndSort(items, orderBy, filter, "cdisc_dm_usubjd"))

  override def listMetadatas(orderBy: String, filter: String) = {
    val futureJsonMetadatas = jsonMetadatas.map(items =>
      filterAndSort(items, orderBy, filter, "field_name"))

    futureJsonMetadatas.map(_.map(json =>
      json.as[Metadata])
    )
  }

  override def listExportFields(orderBy: String, filter: String) = {
    val futureJsonExportFields = jsonFieldNames.map(items =>
      filterAndSort(items, orderBy, filter, "original_field_name"))

    futureJsonExportFields.map(_.map(json =>
      json.as[ExportField])
    )
  }

  override def countRecords(filter: String) =
    jsonRecords.map( items =>
      count(items, filter, "cdisc_dm_usubjd")
    )

  override def getRecord(id: String) =
    jsonRecords.map { items =>
      findBy(items, id, "cdisc_dm_usubjd")}

  override def getMetadata(id: String) =
    jsonMetadatas.map { items =>
      findBy(items, id, "field_name")}

  override def getExportField(id: String) =
    jsonFieldNames.map { items =>
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