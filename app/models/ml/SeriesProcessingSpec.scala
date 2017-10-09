package models.ml

import models.StorageType
import models.json.EnumFormat
import play.api.libs.json.Json

case class SeriesProcessingSpec(
    fieldPath: String,
    processingType: SeriesProcessingType.Value,
    pastValuesCount: Int,
    addInitPaddingWithZeroes: Boolean = true
  ) {

  override def toString =
    if (pastValuesCount == 1)
      fieldPath + "_" + processingType.toString
    else
      fieldPath + "_" + processingType.toString + "-" + pastValuesCount.toString
}

trait DataSetTransformation {
  val core: DataSetTransformationCore
  def resultDataSetId = core.resultDataSetId
  def resultDataSetName = core.resultDataSetName
  def resultStorageType = core.resultStorageType
  def processingBatchSize = core.processingBatchSize
  def saveBatchSize = core.saveBatchSize
}

case class DataSetTransformationCore(
  resultDataSetId: String,
  resultDataSetName: String,
  resultStorageType: StorageType.Value,
  processingBatchSize: Option[Int],
  saveBatchSize: Option[Int]
)

case class DataSetSeriesProcessingSpec(
  sourceDataSetId: String,
  core: DataSetTransformationCore,
  seriesProcessingSpecs: Seq[SeriesProcessingSpec],
  preserveFieldNames: Seq[String]
) extends DataSetTransformation

case class DataSetLink(
  leftSourceDataSetId: String,
  rightSourceDataSetId: String,
  leftLinkFieldNames: Seq[String],
  rightLinkFieldNames: Seq[String],
  leftPreserveFieldNames: Traversable[String],
  rightPreserveFieldNames: Traversable[String],
  core: DataSetTransformationCore
) extends DataSetTransformation {
  def linkFieldNames = leftLinkFieldNames.zip(rightLinkFieldNames)
}

object SeriesProcessingType extends Enumeration {
  val Diff, RelativeDiff, Ratio, LogRatio, Min, Max, Mean = Value
}

object SeriesProcessingSpec {
  implicit val seriesProcessingTypeFormat = EnumFormat.enumFormat(SeriesProcessingType)
  implicit val storageTypeFormat = EnumFormat.enumFormat(StorageType)
  implicit val coreFormat = Json.format[DataSetTransformationCore]
  implicit val seriesProcessingSpecFormat = Json.format[SeriesProcessingSpec]
}