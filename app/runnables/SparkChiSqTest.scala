package runnables

import javax.inject.Inject

import org.apache.commons.math3.stat.inference.OneWayAnova
import org.apache.spark.ml.linalg.{Vector, Vectors}
import services.SparkApp
import org.apache.spark.ml.stat.ChiSquareTest
import org.apache.spark.sql.DataFrame
import services.ml.MachineLearningService

class SparkChiSqTest @Inject() (sparkApp: SparkApp, mlService: MachineLearningService) extends Runnable {

  import sparkApp.sqlContext.implicits._

  override def run = {

    val data = Seq(
      (0.0, Vectors.dense(0.5, 10.0)),
      (0.0, Vectors.dense(1.5, 20.0)),
      (1.0, Vectors.dense(1.5, 30.0)),
      (0.0, Vectors.dense(3.5, 30.0)),
      (0.0, Vectors.dense(3.5, 40.0)),
      (1.0, Vectors.dense(3.5, 40.0))
    )

    val df = data.toDF("label", "features")

    val distinctValues = df.select("label").distinct().map(_.getDouble(0)).collect()

    println("Distinct values: " + distinctValues.mkString(","))

    val newDfs = distinctValues.toSeq.map { value =>
      val pdf = df.filter($"label" === value)
      val newPdf = pdf.sample(false, 0.51)
      println(pdf.count() + " -> " + newPdf.count())
      newPdf
    }

    val newDf = newDfs.tail.foldLeft(newDfs.head) { case (df1: DataFrame, df2: DataFrame) => df1.union(df2): DataFrame }

    println("DF")
    df.show()

    println("New DF")
    newDf.show()

    val results = mlService.independenceTest(df)

    results.foreach( result =>
      println(s"pValue = ${result.pValue}, degree of freedom = ${result.degreeOfFreedom}, statistics = ${result.statistics}")
    )

//    val anova = new OneWayAnova
//    anova.anovaPValue()
  }
}
