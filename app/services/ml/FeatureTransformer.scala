package services.ml

import models.ml.VectorTransformType
import org.apache.spark.ml.feature.{MaxAbsScaler, MinMaxScaler, Normalizer, StandardScaler}
import org.apache.spark.sql.{DataFrame, SparkSession}
import services.SparkUtil

object FeatureTransformer {

  def apply(
    session: SparkSession)(
    data: DataFrame,
    transformType: VectorTransformType.Value,
    inRow: Boolean = false
  ): DataFrame = {
    // aux function to transpose features
    def transpose(columnNames: Traversable[String], df: DataFrame) =
      SparkUtil.transposeVectors(session, columnNames, df)

    // for normalizers we have to switch rows wth columns
    val isNormalizer = transformType == VectorTransformType.L1Normalizer || transformType == VectorTransformType.L2Normalizer

    // check if transpose is needed
    val transposeNeeded = (isNormalizer && !inRow) || (!isNormalizer && inRow)

    // create input df (apply transpose optionally)
    val inputDf = if (transposeNeeded) transpose(Seq("features"), data) else data

    val transformer = transformType match {
      case VectorTransformType.L1Normalizer =>
        new Normalizer()
          .setInputCol("features")
          .setOutputCol("scaledFeatures")
          .setP(1.0)

      case VectorTransformType.L2Normalizer =>
        new Normalizer()
          .setInputCol("features")
          .setOutputCol("scaledFeatures")
          .setP(2.0)

      case VectorTransformType.StandardScaler =>
        new StandardScaler()
          .setInputCol("features")
          .setOutputCol("scaledFeatures")
          .setWithStd(true)
          .setWithMean(true).fit(inputDf)

      case VectorTransformType.MinMaxPlusMinusOneScaler =>
        new MinMaxScaler()
          .setInputCol("features")
          .setOutputCol("scaledFeatures")
          .setMin(-1)
          .setMax(1).fit(inputDf)

      case VectorTransformType.MinMaxZeroOneScaler =>
        new MinMaxScaler()
          .setInputCol("features")
          .setOutputCol("scaledFeatures")
          .setMin(0)
          .setMax(1).fit(inputDf)

      case VectorTransformType.MaxAbsScaler =>
        new MaxAbsScaler()
          .setInputCol("features")
          .setOutputCol("scaledFeatures").fit(inputDf)
    }

    val transformedDf = transformer.transform(inputDf)

    // apply transpose to the output (if applied for the input df)
    if (transposeNeeded) {
      val newDf = transpose(Seq("features", "scaledFeatures"), transformedDf)
      SparkUtil.joinByOrder(data.drop("features"), newDf)
    } else
      transformedDf
  }
}