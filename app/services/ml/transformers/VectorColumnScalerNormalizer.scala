package services.ml.transformers

import models.ml.VectorTransformType
import org.apache.spark.ml.{Estimator, PipelineModel, Transformer}
import org.apache.spark.ml.feature.{MaxAbsScaler, MinMaxScaler, StandardScaler}
import services.SparkUtil

object VectorColumnScalerNormalizer {

  def apply(
    transformType: VectorTransformType.Value
  ): Estimator[_  <: Transformer] =
    apply(transformType, "features", "scaledFeatures")

  def applyInPlace(
    transformType: VectorTransformType.Value,
    inputOutputCol: String
  ): Estimator[PipelineModel] =
    SparkUtil.transformInPlace(
      apply(transformType, inputOutputCol, _),
      inputOutputCol
    )

  def apply(
    transformType: VectorTransformType.Value,
    inputCol: String,
    outputCol: String
  ): Estimator[_  <: Transformer] =
    transformType match {
      case VectorTransformType.L1Normalizer =>
        Normalizer(1, inputCol, outputCol)

      case VectorTransformType.L2Normalizer =>
        Normalizer(2, inputCol, outputCol)

      case VectorTransformType.StandardScaler =>
        new StandardScaler()
          .setInputCol(inputCol)
          .setOutputCol(outputCol)
          .setWithStd(true)
          .setWithMean(true)

      case VectorTransformType.MinMaxPlusMinusOneScaler =>
        new MinMaxScaler()
          .setInputCol(inputCol)
          .setOutputCol(outputCol)
          .setMin(-1)
          .setMax(1)

      case VectorTransformType.MinMaxZeroOneScaler =>
        new MinMaxScaler()
          .setInputCol(inputCol)
          .setOutputCol(outputCol)
          .setMin(0)
          .setMax(1)

      case VectorTransformType.MaxAbsScaler =>
        new MaxAbsScaler()
          .setInputCol(inputCol)
          .setOutputCol(outputCol)
    }
}