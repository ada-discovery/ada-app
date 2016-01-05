package services

import javax.inject.{Inject, Singleton}

import com.fasterxml.jackson.core.JsonParseException
import com.google.inject.ImplementedBy
import play.api.libs.json.{JsObject, JsArray}
import play.api.libs.ws.{WSRequest, WSClient}
import play.api.Play.current
import play.api.Configuration
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import util.JsonUtil._

import models.Dictionary
import models.Field

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}


@ImplementedBy(classOf[RedCapServiceWSImpl])
trait RedCapService {

  def listRecords(orderBy: String, filter: String) : Future[Seq[JsObject]]

  def listMetadatas(orderBy: String, filter: String) : Future[Seq[JsObject]]

  def listFieldNames(orderBy: String, filter: String) : Future[Seq[JsObject]]

  def countRecords(filter: String) : Future[Int]

  def getRecord(id: String) : Future[Seq[JsObject]]

  def getMetadata(id: String) : Future[Seq[JsObject]]

  def getFieldName(id: String) : Future[Seq[JsObject]]

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

  override def listMetadatas(orderBy: String, filter: String) =
    jsonMetadatas.map( items =>
      filterAndSort(items, orderBy, filter, "field_name"))

  override def listFieldNames(orderBy: String, filter: String) =
    jsonFieldNames.map( items =>
      filterAndSort(items, orderBy, filter, "original_field_name"))

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

  override def getFieldName(id: String) =
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
    val finalfields = fieldnames.map( f => Field(f.toString)).toList

    Dictionary(None, "LuxPark REDCap", finalfields)
  }

}
