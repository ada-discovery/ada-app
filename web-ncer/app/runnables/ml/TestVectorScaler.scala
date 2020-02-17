package runnables.ml

import java.{lang => jl}
import javax.inject.Inject

import org.apache.spark.ml.linalg.Vectors
import org.apache.spark.sql.DataFrame
import org.incal.spark_ml.models.VectorScalerType
import org.incal.play.GuiceRunnableApp
import org.incal.spark_ml.transformers._
import org.ada.server.services.SparkApp

class TestVectorScaler @Inject()(sparkApp: SparkApp) extends Runnable {

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
    runAux(dataFrame1, dataFrame1, false)
    runAux(dataFrame1, dataFrame2, false)
  }

  private def runAux(fitDf: DataFrame, testDf: DataFrame, inRow: Boolean) = {
    def scaleVectors(transformType: VectorScalerType.Value) =
      VectorColumnScaler(transformType).fit(fitDf).transform(testDf)

    val l1NormalizedDf = scaleVectors(VectorScalerType.L1Normalizer)
    val l2NormalizedDf = scaleVectors(VectorScalerType.L2Normalizer)
    val standardScaledDf = scaleVectors(VectorScalerType.StandardScaler)
    val minMaxZeroOneScaledDf = scaleVectors(VectorScalerType.MinMaxZeroOneScaler)
    val minMaxPlusMinusOneScaledDf = scaleVectors(VectorScalerType.MinMaxPlusMinusOneScaler)
    val maxAbsScaledDf = scaleVectors(VectorScalerType.MaxAbsScaler)

    println("Original Fit")
    fitDf.show(20, false)

    println("Original Test")
    testDf.show(20, false)

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

object TestVectorScaler extends GuiceRunnableApp[TestVectorScaler]