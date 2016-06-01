package models.synapse

import java.util.Date

import models.EnumFormat
import play.api.libs.json.Json

// http://hud.rel.rest.doc.sagebase.org.s3-website-us-east-1.amazonaws.com/org/sagebionetworks/repo/model/asynch/AsynchJobState.html
object AsyncJobState extends Enumeration {
  val PROCESSING, FAILED,COMPLETE = Value
}

// http://hud.rel.rest.doc.sagebase.org.s3-website-us-east-1.amazonaws.com/org/sagebionetworks/repo/model/asynch/AsynchronousJobStatus.html
case class AsynchronousJobStatus(
  errorMessage: Option[String],
  progressMessage: Option[String],
  progressTotal: Option[Int],
  etag: String,
  jobId: String,
  errorDetails: Option[String],
  exception: Option[String],
  startedByUserId: Int,
  jobState: AsyncJobState.Value,
  progressCurrent: Option[Int],
  changedOn: Date,
  startedOn: Date,
  runtimeMS: Long,
  jobCanceling: Boolean
)

// http://hud.rel.rest.doc.sagebase.org.s3-website-us-east-1.amazonaws.com/org/sagebionetworks/repo/model/table/ColumnType.html
object ColumnType extends Enumeration {
  val STRING, DOUBLE, INTEGER, BOOLEAN, DATE, FILEHANDLEID, ENTITYID, LINK, LARGETEXT = Value
}

// http://hud.rel.rest.doc.sagebase.org.s3-website-us-east-1.amazonaws.com/org/sagebionetworks/repo/model/table/SelectColumn.html
case class SelectColumn(
  id: String,
  columnType:	ColumnType.Value,
  name: String
)

// http://hud.rel.rest.doc.sagebase.org.s3-website-us-east-1.amazonaws.com/org/sagebionetworks/repo/model/table/DownloadFromTableResult.html
case class DownloadFromTableResult(
  headers: Seq[SelectColumn],
  resultsFileHandleId: String,
  concreteType: String,
  etag: String,
  tableId: String
)

case class FileHandle(
  createdOn: Date,
  id: String,
  concreteType: String,
  contentSize: Int,
  createdBy: String,
  etag: String,
  fileName: String,
  contentType: String,
  contentMd5: String,
  storageLocationId: Option[Int]
)

case class Session(
  sessionToken: String,
  acceptsTermsOfUse: Boolean
)

object JsonFormat {
  implicit val AsyncJobStateFormat = EnumFormat.enumFormat(AsyncJobState)
  implicit val AsynchronousJobStatusFormat = Json.format[AsynchronousJobStatus]
  implicit val ColumnTypeFormat = EnumFormat.enumFormat(ColumnType)
  implicit val SelectColumnFormat = Json.format[SelectColumn]
  implicit val DownloadFromTableResultFormat = Json.format[DownloadFromTableResult]
  implicit val FileHandleFormat = Json.format[FileHandle]
  implicit val SessionFormat = Json.format[Session]
}