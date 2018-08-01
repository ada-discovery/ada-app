package runnables

import javax.inject.Inject

import services.{SparkApp, SparkUtil}
import org.apache.spark.ml.linalg.{Vector, Vectors}

class SparkTranspose @Inject() (sparkApp: SparkApp) extends Runnable {

  import sparkApp.sqlContext.implicits._

  private val df = Seq(
    (Vectors.dense(1, 2, 3), -1),
    (Vectors.dense(4, 5, 6), -2),
    (Vectors.dense(7, 8, 9), -3),
    (Vectors.dense(10, 11, 12), -4)
  ).toDF("features", "label")

  override def run = {
    df.show()
    val transDf = SparkUtil.transposeVectors(sparkApp.session, Seq("features"), df)
    transDf.show()
    sparkApp.session.stop()
  }
}

object SparkTranspose extends GuiceBuilderRunnable[SparkTranspose] with App { run }