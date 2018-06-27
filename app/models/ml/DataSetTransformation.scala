package models.ml

import models.StorageType
import models.json.EnumFormat
import play.api.libs.json.Json

trait DataSetTransformation {
  val resultDataSetSpec: ResultDataSetSpec
  def resultDataSetId = resultDataSetSpec.id
  def resultDataSetName = resultDataSetSpec.name
  def resultStorageType = resultDataSetSpec.storageType
}

case class ResultDataSetSpec(
  id: String,
  name: String,
  storageType: StorageType.Value
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
  resultDataSetSpec: ResultDataSetSpec,
  seriesProcessingSpecs: Seq[SeriesProcessingSpec],
  preserveFieldNames: Seq[String],
  processingBatchSize: Option[Int],
  saveBatchSize: Option[Int]
) extends DataSetTransformation

// TODO: This should be merged with DataSetSeriesProcessingSpec
case class DataSetSeriesTransformationSpec(
  sourceDataSetId: String,
  resultDataSetSpec: ResultDataSetSpec,
  seriesTransformationSpecs: Seq[SeriesTransformationSpec],
  preserveFieldNames: Seq[String],
  processingBatchSize: Option[Int],
  saveBatchSize: Option[Int]
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
  processingBatchSize: Option[Int],
  saveBatchSize: Option[Int],
  resultDataSetSpec: ResultDataSetSpec
) extends DataSetTransformation {
  def linkFieldNames = leftLinkFieldNames.zip(rightLinkFieldNames)
}

case class GeneralDataSetLink(
  leftSourceDataSetId: String,
  rightSourceDataSetIds: Seq[String],
  leftLinkFieldNames: Seq[String],
  rightLinkFieldNames: Seq[Seq[String]],
  leftPreserveFieldNames: Traversable[String],
  rightPreserveFieldNames: Seq[Traversable[String]],
  addDataSetIdToRightFieldNames: Boolean,
  saveBatchSize: Option[Int],
  processingBatchSize: Option[Int],
  resultDataSetSpec: ResultDataSetSpec
) extends DataSetTransformation {
  def linkFieldNames = leftLinkFieldNames.zip(rightLinkFieldNames)
}

case class DropFieldsSpec(
  sourceDataSetId: String,
  fieldNamesToDrop: Traversable[String],
  backpressureBufferSize: Int,
  resultDataSetSpec: ResultDataSetSpec,
  processingBatchSize: Option[Int],
  parallelism: Option[Int]
) extends DataSetTransformation

object SeriesProcessingType extends Enumeration {
  val Diff, RelativeDiff, Ratio, LogRatio, Min, Max, Mean = Value
}

object DataSetTransformation {
  implicit val storageTypeFormat = EnumFormat.enumFormat(StorageType)
  implicit val coreFormat = Json.format[ResultDataSetSpec]
  implicit val seriesProcessingTypeFormat = EnumFormat.enumFormat(SeriesProcessingType)
  implicit val seriesProcessingSpecFormat = Json.format[SeriesProcessingSpec]
  implicit val vectorTransformTypeFormat = EnumFormat.enumFormat(VectorTransformType)
  implicit val seriesTransformationSpecFormat = Json.format[SeriesTransformationSpec]
}