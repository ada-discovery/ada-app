package models.sampleRequest


import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.{JsPath, Json, OWrites, Reads}
import reactivemongo.bson.BSONObjectID
import play.api.libs.json.Reads._

/**
  * Information needed to build a data frame for submission to Podium
  *
  * @param dataSetId The ID of the data set
  * @param tableColumnNames The field names that will be submitted
  * @param selectedIds The data set row IDs selected for submission. If empty, all are selected.
  */
case class SampleRequest(
  dataSetId: String,
  tableColumnNames: Seq[String],
  selectedIds: Seq[BSONObjectID]
)

object SampleRequest {
  implicit val BSONObjectIDFormat: BSONObjectIdFormatJson.type = BSONObjectIdFormatJson
  implicit val sampleRequestRead: Reads[SampleRequest] = (
    (JsPath \ "dataSetId").read[String](minLength[String](1)) and
      (JsPath \ "tableColumnNames").read[Seq[String]](verifying[Seq[String]](_.nonEmpty)) and
      (JsPath \ "selectedIds").read[Seq[BSONObjectID]](verifying[Seq[BSONObjectID]](_.nonEmpty))
  )(SampleRequest.apply _)

  implicit val sampleRequestWrite: OWrites[SampleRequest] = Json.writes[SampleRequest]

}

