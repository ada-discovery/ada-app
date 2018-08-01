package services.ml.transformers

import org.apache.spark.ml.feature.VectorAssembler
import org.apache.spark.ml.linalg.SQLDataTypes
import org.apache.spark.ml.{Estimator, Pipeline, PipelineModel, Transformer}
import org.apache.spark.ml.param.{Param, ParamMap}
import org.apache.spark.ml.util.{DefaultParamsWritable, Identifiable}
import org.apache.spark.sql.{DataFrame, Dataset}
import org.apache.spark.sql.types._
import services.SparkUtil

import scala.util.Random

private class SlidingWindowWithConsecutiveOrder(override val uid: String) extends Transformer with DefaultParamsWritable {

  def this() = this(Identifiable.randomUID("sliding_window_with_consecutive_order"))

  protected final val windowSize: Param[Int] = new Param[Int](this, "windowSize", "Sliding window size")
  protected final val inputCol: Param[String] = new Param[String](this, "inputCol", "input column name")
  protected final val orderCol: Param[String] = new Param[String](this, "orderCol", "order column name")
  protected final val outputCol: Param[String] = new Param[String](this, "outputCol", "output column name")

  def setWindowSize(value: Int): this.type = set(windowSize, value)
  def setInputCol(value: String): this.type = set(inputCol, value)
  def setOrderCol(value: String): this.type = set(orderCol, value)
  def setOutputCol(value: String): this.type = set(outputCol, value)

  private val tempInputColPrefix = Random.nextLong()

  override def transform(dataset: Dataset[_]): DataFrame = {
    require($(windowSize) > 0, "Window size must be a positive integer.")

    val df = dataset.toDF()
    val inputOrderDf = df.select(df($(orderCol)), df($(inputCol)))

    // create data sets with an incremented order
    val dataSets = (1 until $(windowSize)).map { i =>
      inputOrderDf
        .withColumn($(orderCol), df($(orderCol)) + i)
        .withColumnRenamed($(inputCol), $(inputCol) + tempInputColPrefix + i)
    }

    val baseVectorizer = new VectorAssembler()
      .setInputCols(Array($(inputCol)))
      .setOutputCol($(outputCol))

    val tempOutputCol = $(outputCol) + Random.nextLong()

    // one-by-one join the base data set with the incremented-order ones
    dataSets.zipWithIndex.foldLeft(baseVectorizer.transform(df)) { case (df1, (df2, i)) =>
      val joinInputCol = $(inputCol) + tempInputColPrefix + (i + 1)

      val vectorizer = new VectorAssembler()
        .setInputCols(Array(joinInputCol, $(outputCol)))
        .setOutputCol(tempOutputCol)

      val joinedDf = df1.join(df2, $(orderCol))

      vectorizer
        .transform(joinedDf)
        .drop($(outputCol), joinInputCol)
        .withColumnRenamed(tempOutputCol, $(outputCol))
    }
  }

  override def copy(extra: ParamMap): SlidingWindowWithConsecutiveOrder = defaultCopy(extra)

  override def transformSchema(schema: StructType): StructType = {
    val existingFields = schema.fields

    require(!existingFields.exists(_.name == $(outputCol)),
      s"Output column ${$(outputCol)} already exists.")

    schema.add(StructField($(outputCol), SQLDataTypes.VectorType, true))
  }
}

object SlidingWindowWithConsecutiveOrder {

  def apply(
    windowSize: Int,
    inputCol: String,
    orderCol: String,
    outputCol: String
  ): Transformer = new SlidingWindowWithConsecutiveOrder().setWindowSize(windowSize).setInputCol(inputCol).setOrderCol(orderCol).setOutputCol(outputCol)

  def applyInPlace(
    windowSize: Int,
    inputOutputCol: String,
    orderCol: String
  ): Estimator[PipelineModel] =
    SparkUtil.transformInPlace(
      apply(windowSize, inputOutputCol, orderCol, _),
      inputOutputCol
    )
}