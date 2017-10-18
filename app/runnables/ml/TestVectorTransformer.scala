package runnables.ml

import java.{lang => jl}
import javax.inject.Inject

import models.ml.VectorTransformType
import org.apache.spark.ml.linalg.Vectors
import org.apache.spark.sql.DataFrame
import runnables.GuiceBuilderRunnable
import services.{RCPredictionService, SparkApp}

class TestVectorTransformer @Inject()(
  rcPredictionService: RCPredictionService,
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
    val newSeries = rcPredictionService.transformSeriesJava(series, VectorTransformType.MinMaxPlusMinusOneScaler)

    println(series)
    println(newSeries)

    runAux(dataFrame1, true)
    runAux(dataFrame2, false)
  }

  private def runAux(df: DataFrame, inRow: Boolean) = {
    val l1NormalizedDf = rcPredictionService.transformVectors(df, VectorTransformType.L1Normalizer, inRow)
    val l2NormalizedDf = rcPredictionService.transformVectors(df, VectorTransformType.L2Normalizer, inRow)
    val standardScaledDf = rcPredictionService.transformVectors(df, VectorTransformType.StandardScaler, inRow)
    val minMaxZeroOneScaledDf = rcPredictionService.transformVectors(df, VectorTransformType.MinMaxZeroOneScaler, inRow)
    val minMaxPlusMinusOneScaledDf = rcPredictionService.transformVectors(df, VectorTransformType.MinMaxPlusMinusOneScaler, inRow)
    val maxAbsScaledDf = rcPredictionService.transformVectors(df, VectorTransformType.MaxAbsScaler, inRow)

    println("Original")
    df.show(20, false)

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
}

object TestVectorTransformer extends GuiceBuilderRunnable[TestVectorTransformer] with App { run }