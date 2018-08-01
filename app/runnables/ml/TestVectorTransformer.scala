package runnables.ml

import java.{lang => jl}
import javax.inject.Inject

import models.ml.VectorTransformType
import org.apache.spark.ml.linalg.Vectors
import org.apache.spark.sql.DataFrame
import runnables.GuiceBuilderRunnable
import services.SparkApp
import services.ml.transformers.{VectorColumnScalerNormalizer, Normalizer, VectorNorm}

class TestVectorTransformer @Inject()(
  sparkApp: SparkApp
  ) extends Runnable {

  private val session = sparkApp.session

  private val dataFrame1 = session.createDataFrame(Seq(
    (0, Vectors.dense(1.0, 0.1, -8.0)),
    (1, Vectors.dense(2.0, 1.0, -4.0)),
    (2, Vectors.dense(4.0, 10.0, 8.0))
  )).toDF("id", "features")

  private val dataFrame2 = session.createDataFrame(Seq(
    (0, Vectors.dense(1.0, 0.1, -8.0)),
    (1, Vectors.dense(2.0, 1.0, -4.0)),
    (2, Vectors.dense(5.0, 11.0, 6.0))
  )).toDF("id", "features")

  private val series: Seq[Seq[jl.Double]] = Seq(
    Seq(1d, 2d, 3d),
    Seq(-8d, 2.5d, 9.4d),
    Seq(0.2d, 0.8d, -1.5d)
  )

  override def run = {
    runAux(dataFrame1, false)
    runAux(dataFrame2, false)
  }

  private def runAux(df: DataFrame, inRow: Boolean) = {
    val l1NormalizedDf = transformVectors(df, VectorTransformType.L1Normalizer, inRow)
    val l2NormalizedDf = transformVectors(df, VectorTransformType.L2Normalizer, inRow)
    val standardScaledDf = transformVectors(df, VectorTransformType.StandardScaler, inRow)
    val minMaxZeroOneScaledDf = transformVectors(df, VectorTransformType.MinMaxZeroOneScaler, inRow)
    val minMaxPlusMinusOneScaledDf = transformVectors(df, VectorTransformType.MinMaxPlusMinusOneScaler, inRow)
    val maxAbsScaledDf = transformVectors(df, VectorTransformType.MaxAbsScaler, inRow)

    println("Original")
    df.show(20, false)

    val l1Norm = VectorNorm(df, "features", 1)
    val l2Norm = VectorNorm(df, "features", 2)
    val l3Norm = VectorNorm(df, "features", 3)
    val linfNorm = VectorNorm(df, "features", Double.PositiveInfinity)

    println("L1 Norm  : " + l1Norm)
    println("L2 Norm  : " + l2Norm)
    println("L3 Norm  : " + l3Norm)
    println("Linf Norm: " + linfNorm)

    println("L1 Normalized")
    l1NormalizedDf.show(20, false)

    println("L2 Normalized")
    l2NormalizedDf.show(20, false)

    println("Standard Scaled")
    standardScaledDf.show(20, false)

    println("Min-Max [0,1] Scaled")
    minMaxZeroOneScaledDf.show(20, false)

    println("Min-Max [-1,1] Scaled")
    minMaxPlusMinusOneScaledDf.show(20, false)

    println("Max-Abs Scaled")
    maxAbsScaledDf.show(20, false)
  }

  private def transformVectors(
    data: DataFrame,
    transformType: VectorTransformType.Value,
    inRow: Boolean = false
  ): DataFrame =
    VectorColumnScalerNormalizer(transformType).fit(data).transform(data)
}

object TestVectorTransformer extends GuiceBuilderRunnable[TestVectorTransformer] with App { run }