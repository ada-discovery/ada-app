package models.ml

import reactivemongo.play.json.BSONFormats._
import play.api.libs.json.{Format, Json}
import reactivemongo.bson.BSONObjectID

case class RCPredictionSetting(
  reservoirNodeNum: Int,
  reservoirInDegree: Int,
  inputReservoirConnectivity: Double,
  reservoirSpectralRadius: Double,
  washoutPeriod: Int,
  dropRightLength: Int,
  inputSeriesFieldPaths: Seq[String],
  outputSeriesFieldPaths: Seq[String],
  sourceDataSetId: String,
  resultDataSetId: String,
  resultDataSetName: String,
  batchSize: Option[Int]
)

case class RCPredictionSettingAndResults(
  _id: Option[BSONObjectID],
  reservoirNodeNum: Int,
  reservoirInDegree: Int,
  inputReservoirConnectivity: Double,
  reservoirSpectralRadius: Double,
  washoutPeriod: Int,
  dropRightLength: Int,
  inputSeriesFieldPaths: Seq[String],
  outputSeriesFieldPaths: Seq[String],
  sourceDataSetId: String,
  resultDataSetId: String,
  resultDataSetName: String,
  meanSampLast: Double,
  meanRnmseLast: Double
)

object RCPredictionSettingAndResults {
  implicit val rcPredictionSettingAndResultsFormat = Json.format[RCPredictionSettingAndResults]
}