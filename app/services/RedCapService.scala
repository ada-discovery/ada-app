package services

import javax.inject.{Inject, Singleton}

import com.fasterxml.jackson.core.JsonParseException
import com.google.inject.ImplementedBy
import play.api.libs.json.{JsObject, JsArray}
import play.api.libs.ws.{WSRequest, WSClient}
import play.api.Configuration
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import util.JsonUtil._

import models.Dictionary
import models.Field
import models.redcap.JsonFormat._
import models.redcap._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

@ImplementedBy(classOf[RedCapServiceWSImpl])
trait RedCapService {

  /**
    * Retrieve all fields matching the filtering criterion and order them according to a reference field.
    * @param orderBy Reference field for ordering/ sorting.
    * @param filter Field for macthing and filtering the reference field.
    * @return Sorted records matching filter criterion.
    */
  def listRecords(orderBy: String, filter: String) : Future[Seq[JsObject]]

  /**
    * Retrieve all metadata fields matching the filtering criterion and order them according to a reference field.
    * @param orderBy Reference field for ordering/ sorting.
    * @param filter Field for macthing and filtering the reference field.
    * @return Sorted records matching filter criterion.
    */
  def listMetadatas(orderBy: String, filter: String) : Future[Seq[Metadata]]

  /**
    * Create list of all field names. Field names are sorted and filtered if they don't match filter criterion.
    * @param orderBy Reference field for sorting.
    * @param filter Specify filter to exclude fields from name list.
    * @return Filtered and sorted list of records.
    */
  def listExportFields(orderBy: String, filter: String) : Future[Seq[ExportField]]

  /**
    * Count all records in reference field.
    * @param filter Value for filtering reference field.
    * @return The number of records matching the filter string.
    */
  def countRecords(filter: String) : Future[Int]

  /**
    * Get the name of the field specified by given id string.
    * @param id String for matching with reference field.
    * @return Json representation of matching record.
    */
  def getRecord(id: String) : Future[Seq[JsObject]]

  /**
    * Retrieve the metadata matching the filter.
    * @param id
    * @return
    */
  def getMetadata(id: String) : Future[Seq[JsObject]]

  /**
    * Get the name of the field(s) specified by given id string.
    * @param id String for matching with reference field.
    * @return Sequence of field(s) matching id.
    */
  def getExportField(id: String) : Future[Seq[JsObject]]

  /**
    * Generate a Dictionary (i.e. for matching with other Dictionary objects).
    * @see models.Dictionary
    * @return Dictionary object containing the data of the assigned REDCap instance.
    */
  def getDictionary : Dictionary
}

@Singleton
protected class RedCapServiceWSImpl @Inject() (
    ws: WSClient,
    configuration: Configuration
  ) extends RedCapService {

//  implicit val sslClient = NingWSClient()

//  val req: WSRequest = sslClient.url(current.configuration.getString("redcap.prodserver.api.url").get)

  val req: WSRequest = ws.url(configuration.getString("redcap.prodserver.api.url").get)

  val baseRequestData = Map(
    "token" -> configuration.getString("redcap.prodserver.token").get,
    "format" -> "json"
  )

  val recordRequestData = baseRequestData ++ Map("content" -> "record", "type" -> "flat")
  val metadataRequestData = baseRequestData ++ Map("content" -> "metadata")
  val fieldNamesRequestData = baseRequestData ++ Map("content" -> "exportFieldNames")

  // Primitive cache

  lazy val jsonRecords = runRedCapQuery(recordRequestData)

  lazy val jsonMetadatas = runRedCapQuery(metadataRequestData)

  lazy val jsonFieldNames = runRedCapQuery(fieldNamesRequestData)

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
        response.json.as[JsArray].value.asInstanceOf[Seq[JsObject]]
      } catch {
        case e: JsonParseException => {
          println(response.toString())
          List[JsObject]()
        }
      }
    }

  /**
    * Generate dictionary
    * TODO: incomplete, partially implemented for testing
    *
    * */
  override def getDictionary = {
    val fieldnamesFuture = listRecords("", "")
    val fieldnames: Seq[JsObject] = Await.result(fieldnamesFuture, 120000 millis)
    val finalfields = fieldnames.map( f => Field(f.toString, models.FieldType.String)).toList

    Dictionary(None, "LuxPark REDCap", finalfields)
  }
}