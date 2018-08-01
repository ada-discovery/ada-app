package services.ml.transformers

import org.apache.spark.ml.{Estimator, PipelineModel, Transformer}
import org.apache.spark.ml.param.{Param, ParamMap}
import org.apache.spark.ml.util.{DefaultParamsWritable, Identifiable}
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.{DataFrame, Dataset}
import org.apache.spark.sql.types._
import org.apache.spark.sql.functions.{col, last}
import services.SparkUtil

private class SeqShift(override val uid: String) extends Transformer with DefaultParamsWritable {

  def this() = this(Identifiable.randomUID("seq_shift"))

  protected final val shift: Param[Int] = new Param[Int](this, "shift", "shift")
  protected final val inputCol: Param[String] = new Param[String](this, "inputCol", "input column name")
  protected final val orderCol: Param[String] = new Param[String](this, "orderCol", "order column name")
  protected final val outputCol: Param[String] = new Param[String](this, "outputCol", "output column name")

  def setShift(value: Int): this.type = set(shift, value)
  def setInputCol(value: String): this.type = set(inputCol, value)
  def setOrderCol(value: String): this.type = set(orderCol, value)
  def setOutputCol(value: String): this.type = set(outputCol, value)

  override def transform(dataset: Dataset[_]): DataFrame = {
    require($(shift) > 0, "Shift must be a non-negative integer.")

    // data frame with a window
    val windowSpec = Window.orderBy($(orderCol)).rowsBetween(0, $(shift))
    val shiftedDf = dataset.withColumn($(outputCol), last(dataset($(inputCol))).over(windowSpec))

    // drop the last "shift" items
    val cutOrderValue = dataset.select(col($(orderCol)))
      .orderBy(dataset($(orderCol)).desc)
      .limit($(shift))
      .collect().last.getInt(0)

    shiftedDf.where(dataset($(orderCol)) < cutOrderValue)
  }

  override def copy(extra: ParamMap): SeqShift = defaultCopy(extra)

  override def transformSchema(schema: StructType): StructType = {
    val inputColName = $(inputCol)
    val outputColName = $(outputCol)

    val existingFields = schema.fields

    val inputField = schema(inputColName)
    val outputField = inputField.copy(name = outputColName)

    require(!existingFields.exists(_.name == outputColName),
      s"Output column $outputColName already exists.")

    schema.add(outputField)
  }
}

object SeqShift {

  def apply(
    shift: Int,
    inputCol: String,
    orderCol: String,
    outputCol: String
  ): Transformer = new SeqShift().setShift(shift).setInputCol(inputCol).setOrderCol(orderCol).setOutputCol(outputCol)

  def applyInPlace(
    shift: Int,
    inputOutputCol: String,
    orderCol: String
  ): Estimator[PipelineModel] =
    SparkUtil.transformInPlace(
      apply(shift, inputOutputCol, orderCol, _),
      inputOutputCol
    )
}