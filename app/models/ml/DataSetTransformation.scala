package models.ml

import models.StorageType
import models.json.EnumFormat
import play.api.libs.json.Json

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

case class DataSetSeriesProcessingSpec(
  sourceDataSetId: String,
  core: DataSetTransformationCore,
  seriesProcessingSpecs: Seq[SeriesProcessingSpec],
  preserveFieldNames: Seq[String]
) extends DataSetTransformation

// TODO: This should be merged with DataSetSeriesProcessingSpec
case class DataSetSeriesTransformationSpec(
  sourceDataSetId: String,
  core: DataSetTransformationCore,
  seriesTransformationSpecs: Seq[SeriesTransformationSpec],
  preserveFieldNames: Seq[String]
) extends DataSetTransformation

case class SeriesTransformationSpec(
  fieldPath: String,
  transformType: VectorTransformType.Value
) {
  override def toString =
    fieldPath + "_" + transformType.toString
}

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

object DataSetTransformation {
  implicit val storageTypeFormat = EnumFormat.enumFormat(StorageType)
  implicit val coreFormat = Json.format[DataSetTransformationCore]
  implicit val seriesProcessingTypeFormat = EnumFormat.enumFormat(SeriesProcessingType)
  implicit val seriesProcessingSpecFormat = Json.format[SeriesProcessingSpec]
  implicit val vectorTransformTypeFormat = EnumFormat.enumFormat(VectorTransformType)
  implicit val seriesTransformationSpecFormat = Json.format[SeriesTransformationSpec]
}