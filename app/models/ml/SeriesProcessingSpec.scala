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

case class DataSetSeriesProcessingSpec(
  sourceDataSetId: String,
  resultDataSetId: String,
  resultDataSetName: String,
  resultStorageType: StorageType.Value,
  seriesProcessingSpecs: Seq[SeriesProcessingSpec],
  preserveFieldNames: Seq[String],
  processingBatchSize: Option[Int],
  saveBatchSize: Option[Int]
)

object SeriesProcessingType extends Enumeration {
  val Diff, RelativeDiff, Ratio, LogRatio, Min, Max, Mean = Value
}

object SeriesProcessingSpec {
  implicit val seriesProcessingTypeFormat = EnumFormat.enumFormat(SeriesProcessingType)
  implicit val seriesProcessingSpecFormat = Json.format[SeriesProcessingSpec]
}