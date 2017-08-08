package runnables

import javax.inject.Inject

import models.ml.VectorTransformType
import org.apache.spark.ml.feature.VectorAssembler
import org.apache.spark.ml.linalg.{DenseVector, Vectors}
import org.apache.spark.sql.functions.{col, struct, udf}
import org.apache.spark.sql.{DataFrame, Row}
import services.SparkApp
import services.ml.MachineLearningService

class TestVectorTransformer @Inject()(
    mlService: MachineLearningService,
    sparkApp: SparkApp
  ) extends Runnable {

  private val session = sparkApp.session

  import session.implicits._

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
    runAux(dataFrame1)
    runAux(dataFrame2)
  }

  private def runAux(df: DataFrame) = {
    val featureCount = df.select("features").head().getAs[DenseVector](0).size
    println(featureCount)

    def assembleFunc(i: Int) = udf { r: Row =>
      r.getAs[DenseVector](0)(i)
    }

    val columns = for (i <- 0 until featureCount) yield {
      assembleFunc(i)(struct("features")).as("f_" + i)
    }

    val newDf = df.select(columns :_ *)

    newDf.show(20, false)

    val rowValues = newDf.select("f_0").collect().map(_.getDouble(0))

    Row(Vectors.dense(rowValues))

//    df1.show(20, false)
//
//    val df2 = df.select("features").map(_.getAs[DenseVector](0)(1))
//    df2.show(20, false)
//
//    val df3 = df.select("features").map(_.getAs[DenseVector](0)(2))
//    df3.show(20, false)

    val normalizedDf = mlService.transformVectors(df, VectorTransformType.Normalizer)
    val standardScaledDf = mlService.transformVectors(df, VectorTransformType.StandardScaler)
    val minMaxScaledDf = mlService.transformVectors(df, VectorTransformType.MinMaxScaler)
    val maxAbsScaledDf = mlService.transformVectors(df, VectorTransformType.MaxAbsScaler)

    println("Normalized")
    normalizedDf.show(20, false)

    println("Standard Scaled")
    standardScaledDf.show(20, false)

    println("Min-Max Scaled")
    minMaxScaledDf.show(20, false)

    println("Max-Abs Scaled")
    maxAbsScaledDf.show(20, false)
  }
}

object TestVectorTransformer extends GuiceBuilderRunnable[TestVectorTransformer] with App { run }