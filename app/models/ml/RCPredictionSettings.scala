package models.ml

import dataaccess._
import models.json.{EnumFormat, OrdinalSortedEnumFormat}
import controllers.FlattenFormat
import reactivemongo.play.json.BSONFormats._
import play.api.libs.json.{Format, JsValue, Json}
import reactivemongo.bson.BSONObjectID
import util.FieldUtil
import java.{util => ju}

import com.banda.incal.domain.ReservoirLearningSetting

case class RCPredictionSettings(
  reservoirNodeNums: Seq[Int],
  reservoirInDegrees: Seq[Int],
  inputReservoirConnectivities: Seq[Double],
  reservoirSpectralRadiuses: Seq[Double],
  inScales: Seq[Double],
  seriesPreprocessingType: Option[VectorTransformType.Value],
  predictAheads: Seq[Int],
  inputSeriesFieldPaths: Seq[String],
  outputSeriesFieldPaths: Seq[String],
  washoutPeriod: Int,
  dropLeftLength: Option[Int],
  dropRightLength: Option[Int],
  seriesLength: Option[Int],
  sourceDataSetId: String,
  resultDataSetId: String,
  resultDataSetName: String,
  resultDataSetIndex: Option[Int],
  batchSize: Option[Int],
  preserveWeightFieldNames: Seq[String]
)

class ExtendedReservoirLearningSetting extends ReservoirLearningSetting {

  private[this] var _predictAhead: Int = _

  private[this] var _seriesPreprocessingType: Option[_root_.models.ml.VectorTransformType.Value] = None

  def predictAhead: Int = _predictAhead

  def predictAhead_=(value: Int) = _predictAhead = value

  def seriesPreprocessingType = _seriesPreprocessingType

  def seriesPreprocessingType_=(value: Option[VectorTransformType.Value]) =_seriesPreprocessingType = value
}

case class RCPredictionInputOutputSpec(
  inputSeriesFieldPaths: Seq[String],
  outputSeriesFieldPaths: Seq[String],
  dropLeftLength: Option[Int] = None,
  dropRightLength: Option[Int] = None,
  seriesLength: Option[Int] = None,
  sourceDataSetId: String,
  resultDataSetId: String,
  resultDataSetName: String
)

case class RCPredictionSetting(
  reservoirNodeNum: Int,
  reservoirInDegree: Int,
  inputReservoirConnectivity: Double,
  reservoirSpectralRadius: Double,
  inScale: Double,
  seriesPreprocessingType: Option[VectorTransformType.Value],
  washoutPeriod: Int,
  predictAhead: Int
)

case class RCPredictionSettingAndResults(
  _id: Option[BSONObjectID],
  setting: RCPredictionSetting,
  inputOutputSpec: RCPredictionInputOutputSpec,
  meanSampLast: Double,
  meanRnmseLast: Double,
  timeCreated: ju.Date = new ju.Date()
)

object RCPredictionSettingAndResults {
  implicit val rcPredictionInputOutputSpecFormat = Json.format[RCPredictionInputOutputSpec]
  implicit val vectorTransformTypeFormat = OrdinalSortedEnumFormat.enumFormat(VectorTransformType)
  implicit val rcPredictionSettingFormat = Json.format[RCPredictionSetting]
  implicit val rcPredictionSettingAndResultsFormat = new FlattenFormat(Json.format[RCPredictionSettingAndResults], "-")
}

object XXX extends App {

  val setting = RCPredictionSetting(20, 20, 0.1, 0.5, 10, Some(VectorTransformType.MinMaxPlusMinusOneScaler), 10, 1)

  val ioSpec = RCPredictionInputOutputSpec(Seq("lla", "lll"), Seq("a", "bb"), Some(3), None, Some(100), "dataset1", "dataset2", "datasetname")

  val settingAndResults = RCPredictionSettingAndResults(None, setting, ioSpec, 0.9, 0.87)

  val json = Json.toJson(settingAndResults)

  println(Json.prettyPrint(json))
  println(json.as[RCPredictionSettingAndResults])

  println("Case Fields:")
  ReflectionUtil.getCaseClassMemberAndTypeNames[RCPredictionSetting].foreach(println(_))

  println
  println("Fields:")
  FieldUtil.caseClassToFlatFieldTypes[RCPredictionSetting]().foreach(println(_))
  println

  println("Case Fields:")
  ReflectionUtil.getCaseClassMemberAndTypeNames[RCPredictionInputOutputSpec].foreach(println(_))

  println
  println("Fields:")
  FieldUtil.caseClassToFlatFieldTypes[RCPredictionInputOutputSpec]().foreach(println(_))

  println("Case Fields:")
  ReflectionUtil.getCaseClassMemberAndTypeNames[RCPredictionSettingAndResults].foreach(println(_))

  println
  println("Fields:")
  FieldUtil.caseClassToFlatFieldTypes[RCPredictionSettingAndResults]("-").foreach(println(_))
}