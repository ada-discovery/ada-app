package services

import javax.inject.{Inject, Singleton}

import com.fasterxml.jackson.core.JsonParseException
import com.google.inject.ImplementedBy
import play.api.libs.json.{JsObject, JsArray}
import play.api.libs.ws.{WSRequest, WSClient}
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import util.JsonUtil._

import scala.concurrent.Future

@ImplementedBy(classOf[RedCapServiceWSImpl])
trait RedCapService {

  def listRecords(page: Int, orderBy: String, filter: String) : Future[Seq[JsObject]]

  def listMetadatas(page: Int, orderBy: String, filter: String) : Future[Seq[JsObject]]

  def listFieldNames(page: Int, orderBy: String, filter: String) : Future[Seq[JsObject]]

  def countRecords(filter: String) : Future[Int]

  def getRecord(id: String) : Future[Seq[JsObject]]

  def getMetadata(id: String) : Future[Seq[JsObject]]

  def getFieldName(id: String) : Future[Seq[JsObject]]
}

@Singleton
protected class RedCapServiceWSImpl @Inject() (ws: WSClient) extends RedCapService {

//  implicit val sslClient = NingWSClient()

//  val req: WSRequest = sslClient.url(current.configuration.getString("redcap.prodserver.api.url").get)

  val req: WSRequest = ws.url(current.configuration.getString("redcap.prodserver.api.url").get)

  val baseRequestData = Map(
    "token" -> current.configuration.getString("redcap.prodserver.token").get,
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

  override def listRecords(page: Int, orderBy: String, filter: String) =
    jsonRecords.map( items =>
      filterAndSort(items, orderBy, filter, "cdisc_dm_usubjd"))

  override def listMetadatas(page: Int, orderBy: String, filter: String) =
    jsonMetadatas.map( items =>
      filterAndSort(items, orderBy, filter, "field_name"))

  override def listFieldNames(page: Int, orderBy: String, filter: String) =
    jsonFieldNames.map( items =>
      filterAndSort(items, orderBy, filter, "original_field_name"))

  override def countRecords(filter: String) =
    jsonFieldNames.map( items =>
      filterAndSort(items, "", filter, "cdisc_dm_usubjd").length
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
}
