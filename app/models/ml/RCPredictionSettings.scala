package models.ml

import org.ada.server.dataaccess._
import org.incal.core.util.ReflectionUtil
import org.ada.server.json.{EnumFormat, FlattenFormat, JavaOrdinalEnumFormat, OrdinalSortedEnumFormat}
import reactivemongo.play.json.BSONFormats._
import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID
import util.FieldUtil
import java.{util => ju}

import com.banda.network.domain.{ActivationFunctionType, ReservoirLearningSetting}
import org.incal.spark_ml.models.VectorScalerType

case class RCPredictionSettings(
  reservoirNodeNums: Seq[Int],
  inputReservoirConnectivities: Seq[Double],
  reservoirSpectralRadiuses: Seq[Double],
  inScales: Seq[Double],
  predictAheads: Seq[Int],
  reservoirInDegrees: Option[Seq[Int]],
  reservoirCircularInEdges: Option[Seq[Int]],
  reservoirFunctionType: ActivationFunctionType,
  reservoirFunctionParams: Option[Seq[Double]],
  seriesPreprocessingType: Option[VectorScalerType.Value],
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

  private[this] var _seriesPreprocessingType: Option[VectorScalerType.Value] = None

  def predictAhead: Int = _predictAhead

  def predictAhead_=(value: Int) = _predictAhead = value

  def seriesPreprocessingType = _seriesPreprocessingType

  def seriesPreprocessingType_=(value: Option[VectorScalerType.Value]) =_seriesPreprocessingType = value
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
  reservoirInDegree: Option[Int],
  reservoirCircularInEdges: Option[Seq[Int]],
  reservoirFunctionType: ActivationFunctionType,
  reservoirFunctionParams: Option[Seq[Double]],
  inputReservoirConnectivity: Double,
  reservoirSpectralRadius: Double,
  inScale: Double,
  seriesPreprocessingType: Option[VectorScalerType.Value],
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
  implicit val vectorTransformTypeFormat = OrdinalSortedEnumFormat(VectorScalerType)
  implicit val activationFunctionTypeFormat = JavaOrdinalEnumFormat[ActivationFunctionType]
  implicit val rcPredictionSettingFormat = Json.format[RCPredictionSetting]
  implicit val rcPredictionSettingAndResultsFormat = new FlattenFormat(Json.format[RCPredictionSettingAndResults], "-")
}

object XXX extends App {

  val setting = RCPredictionSetting(20, Some(20), None, ActivationFunctionType.Tanh, None, 0.1, 0.5, 10, Some(VectorScalerType.MinMaxPlusMinusOneScaler), 10, 1)

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