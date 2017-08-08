package runnables

import javax.inject.Inject

import models.ml.VectorTransformType
import org.apache.spark.ml.linalg.Vectors
import org.apache.spark.sql.DataFrame
import services.SparkApp
import services.ml.MachineLearningService

class TestVectorTransformer @Inject()(
    mlService: MachineLearningService,
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

  override def run = {
    runAux(dataFrame1, true)
    runAux(dataFrame2, false)
  }

  private def runAux(df: DataFrame, inRow: Boolean) = {
    val l1NormalizedDf = mlService.transformVectors(df, VectorTransformType.L1Normalizer, inRow)
    val l2NormalizedDf = mlService.transformVectors(df, VectorTransformType.L2Normalizer, inRow)
    val standardScaledDf = mlService.transformVectors(df, VectorTransformType.StandardScaler, inRow)
    val minMaxZeroOneScaledDf = mlService.transformVectors(df, VectorTransformType.MinMaxZeroOneScaler, inRow)
    val minMaxPlusMinusOneScaledDf = mlService.transformVectors(df, VectorTransformType.MinMaxPlusMinusOneScaler, inRow)
    val maxAbsScaledDf = mlService.transformVectors(df, VectorTransformType.MaxAbsScaler, inRow)

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