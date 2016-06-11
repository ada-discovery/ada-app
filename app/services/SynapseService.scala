package services

import javax.inject.Inject

import com.google.inject.assistedinject.Assisted
import models.synapse._
import play.api.libs.ws.{WSRequest, WSResponse, WSClient}
import play.api.Configuration
import util.ExecutionContexts
import play.api.libs.json.{Json, JsObject}
import scala.concurrent.Future
import scala.concurrent.Await.result
import scala.concurrent.duration._
import models.synapse.JsonFormat._

trait SynapseServiceFactory {
  def apply(@Assisted("username") username: String, @Assisted("password") password: String): SynapseService
}

trait SynapseService {

  /**
    * (Re)logins and refreshes a session token
    */
  def login: Future[Unit]

  /**
    * Prolonges a current session token for another 24 hours
    */
  def prolongSession: Future[Unit]

  /**
    * .Runs a table query and return a token
    */
  def runCsvTableQuery(tableId: String, sql: String): Future[String]

  /**
    * Gets results or a job status if still running
    */
  def getCsvTableResult(tableId: String, jobToken: String): Future[Either[DownloadFromTableResult, AsynchronousJobStatus]]

  /**
    * Gets results... wait till it's done by polling
    */
  def getCsvTableResultWait(tableId: String, jobToken: String): Future[DownloadFromTableResult]

  /**
    * Gets a file handle
    */
  def getFileHandle(fileHandleId: String): Future[FileHandle]

  /**
    * Downloads a file by following a URL for a given file handle id.
    * Only the person who created the FileHandle can download the file.
    * To download column files use downloadColumnFile
    *
    * @see http://hud.rel.rest.doc.sagebase.org.s3-website-us-east-1.amazonaws.com/GET/fileHandle/handleId/url.html
    */
  def downloadFile(fileHandleId: String): Future[String]

  /**
    * Gets a table as csv by combining runCsvTableQuery, getCsvTableResults, and downloadFile
    */
  def getTableAsCsv(tableId: String): Future[String]

  /**
    * Gets the column models of a given table
    */
  def getTableColumnModels(tableId: String): Future[PaginatedColumnModels]

  /**
    * Gets a list of file handles associated with the rows and columns specified in a row ref set
    */
  def getTableColumnFileHandles(rowReferenceSet: RowReferenceSet): Future[TableFileHandleResults]

  /**
    * Downloads a file associated with given column and row.
    */
  def downloadColumnFile(tableId: String, columnId: String, rowId: Int, versionNumber: Int): Future[String]
}

protected[services] class SynapseServiceWSImpl @Inject() (
    @Assisted("username") private val username: String,
    @Assisted("password") private val password: String,
    ws: WSClient,
    configuration: Configuration
  ) extends SynapseService {

  private val baseUrl = configuration.getString("synapse.api.rest.url").get
  private val loginSubUrl = configuration.getString("synapse.api.login.url").get
  private val prolongSessionUrl = configuration.getString("synapse.api.session.url").get
  private val tableCsvDownloadStartSubUrl1 = configuration.getString("synapse.api.table_csv_download_start.url.part1").get
  private val tableCsvDownloadStartSubUrl2 = configuration.getString("synapse.api.table_csv_download_start.url.part2").get
  private val tableCsvDownloadResultSubUrl1 = configuration.getString("synapse.api.table_csv_download_result.url.part1").get
  private val tableCsvDownloadResultSubUrl2 = configuration.getString("synapse.api.table_csv_download_result.url.part2").get
  private val fileHandleSubUrl = configuration.getString("synapse.api.file_handle.url").get
  private val fileDownloadSubUrl1 = configuration.getString("synapse.api.file_download.url.part1").get
  private val fileDownloadSubUrl2 = configuration.getString("synapse.api.file_download.url.part2").get
  private val columnModelsSubUrl1 = configuration.getString("synapse.api.table_column_models.url.part1").get
  private val columnModelsSubUrl2 = configuration.getString("synapse.api.table_column_models.url.part2").get
  private val columnFileHandleSubUrl1 = configuration.getString("synapse.api.column_file_handles.url.part1").get
  private val columnFileHandleSubUrl2 = configuration.getString("synapse.api.column_file_handles.url.part2").get
  private val fileColumnDownloadSubUrl1 = configuration.getString("synapse.api.file_column_download.url.part1").get
  private val fileColumnDownloadSubUrl2 = configuration.getString("synapse.api.file_column_download.url.part2").get
  private val fileColumnDownloadSubUrl3 = configuration.getString("synapse.api.file_column_download.url.part3").get
  private val fileColumnDownloadSubUrl4 = configuration.getString("synapse.api.file_column_download.url.part4").get
  private val fileColumnDownloadSubUrl5 = configuration.getString("synapse.api.file_column_download.url.part5").get

  private val timeout = 120000 millis
  private val tableCsvResultPollingFreq = 200

  private implicit val executionContext = ExecutionContexts.SynapseExecutionContext

  private var sessionToken: Option[String] = None

  /**
    * Requests
    */
  val loginReq =
    withJsonContent(
      ws.url(baseUrl + loginSubUrl)
    )

  def prolongSessionReq =
    ws.url(baseUrl + prolongSessionUrl)

  def startTableCsvDownloadReq(tableId: String) =
    withJsonContent(
      withSessionToken(
        ws.url(baseUrl + tableCsvDownloadStartSubUrl1 + tableId + tableCsvDownloadStartSubUrl2)
      )
    )

  def getTableCsvDownloadResultReq(tableId: String, jobToken: String) =
    withSessionToken(
      ws.url(baseUrl + tableCsvDownloadResultSubUrl1 + tableId + tableCsvDownloadResultSubUrl2 + jobToken)
    )

  def getFileHandleReq(fileHandleId: String) =
    withSessionToken(
      ws.url(baseUrl + fileHandleSubUrl + fileHandleId)
    )

  def downloadFileReq(fileHandleId: String) =
    withSessionToken(
      ws.url(baseUrl + fileDownloadSubUrl1 + fileHandleId + fileDownloadSubUrl2)
    )

  def getColumnModelsReq(tableId: String) =
    withSessionToken(
      ws.url(baseUrl + columnModelsSubUrl1 + tableId + columnModelsSubUrl2)
    )

  def getColumnFileHandlesReq(tableId: String) =
    withJsonContent(
      withSessionToken(
        ws.url(baseUrl + columnFileHandleSubUrl1 + tableId + columnFileHandleSubUrl2)
      )
    )

  def downloadColumnFileReq(tableId: String, columnId: String, rowId: Int, versionNumber: Int) =
    withSessionToken(
      ws.url(baseUrl + fileColumnDownloadSubUrl1 + tableId + fileColumnDownloadSubUrl2 + columnId +
        fileColumnDownloadSubUrl3 + rowId + fileColumnDownloadSubUrl4 + versionNumber + fileColumnDownloadSubUrl5)
    )

  def withSessionToken(request: WSRequest): WSRequest =
    request.withHeaders("sessionToken" -> getSessionToken)

  def withJsonContent(request: WSRequest): WSRequest =
    request.withHeaders("Content-Type" -> "application/json")

  override def login: Future[Unit] = {
    val data = Json.obj("username" -> username, "password" -> password)
    loginReq.post(data).map { response =>
      val newSessionToken = (response.json.as[JsObject] \ "sessionToken").get.as[String]
      sessionToken = Some(newSessionToken)
    }
  }

  override def prolongSession: Future[Unit] =
    prolongSessionReq.put(SessionFormat.writes(Session(getSessionToken, true))).map { response =>
      handleErrorResponse(response)
    }

  override def runCsvTableQuery(tableId: String, sql: String): Future[String] = {
    val data = Json.obj("sql" -> sql)
    startTableCsvDownloadReq(tableId).post(data).map { response =>
      handleErrorResponse(response)
      (response.json.as[JsObject] \ "token").get.as[String]
    }
  }

  override def getCsvTableResult(tableId: String, jobToken: String): Future[Either[DownloadFromTableResult, AsynchronousJobStatus]] =
    getTableCsvDownloadResultReq(tableId, jobToken).get.map { response =>
      handleErrorResponse(response)
      val json = response.json
      response.status match {
        case 201 => Left(json.as[DownloadFromTableResult])
        case 202 => Right(json.as[AsynchronousJobStatus])
      }
    }

  override def getCsvTableResultWait(tableId: String, jobToken: String): Future[DownloadFromTableResult] = Future {
    var res: Either[DownloadFromTableResult, AsynchronousJobStatus] = null
    while ({res = result(getCsvTableResult(tableId, jobToken), timeout); res.isRight}) {
      Thread.sleep(tableCsvResultPollingFreq)
    }
    res.left.get
  }

  override def getFileHandle(fileHandleId: String): Future[FileHandle] =
    getFileHandleReq(fileHandleId).get.map { response =>
      handleErrorResponse(response)
      response.json.as[FileHandle]
    }

  override def downloadFile(fileHandleId: String): Future[String] =
    downloadFileReq(fileHandleId).withFollowRedirects(true).get.map { response =>
      handleErrorResponse(response)
      response.body
    }

  override def getTableAsCsv(tableId: String): Future[String] =
    for {
      jobToken <- runCsvTableQuery(tableId, s"SELECT * FROM $tableId")
      result <- getCsvTableResultWait(tableId, jobToken)
      content <- downloadFile(result.resultsFileHandleId)
    } yield
      content

  override def getTableColumnFileHandles(rowReferenceSet: RowReferenceSet): Future[TableFileHandleResults] =
    getColumnFileHandlesReq(rowReferenceSet.tableId).post(Json.toJson(rowReferenceSet)).map { response =>
      handleErrorResponse(response)
      response.json.as[TableFileHandleResults]
    }

  override def downloadColumnFile(tableId: String, columnId: String, rowId: Int, versionNumber: Int): Future[String] =
    downloadColumnFileReq(tableId, columnId, rowId, versionNumber).withFollowRedirects(true).get.map { response =>
      handleErrorResponse(response)
      response.body
    }

  private def handleErrorResponse(response: WSResponse): Unit =
    response.status match {
      case x if x >= 200 && x<= 299 => ()
      case 401 => throw new UnauthorizedAccessRestException(response.statusText)
      case _ => throw new RestException(response.status + ": " + response.statusText + "; " + response.body)
    }

  // could be used to automatically reauthorize...
  private def accessRetry[T](action: => T): T =
    try {
      action
    } catch {
      case e: UnauthorizedAccessRestException => { login; action }
    }

  private def getSessionToken = synchronized {
    if (sessionToken.isEmpty) result(login, timeout)
    sessionToken.get
  }

  override def getTableColumnModels(tableId: String): Future[PaginatedColumnModels] =
    getColumnModelsReq(tableId).get.map { response =>
      handleErrorResponse(response)
      response.json.as[PaginatedColumnModels]
    }
}