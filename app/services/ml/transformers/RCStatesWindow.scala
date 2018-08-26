package services.ml.transformers

import javax.inject.Inject

import com.banda.network.business.learning.ReservoirRunnableFactory
import com.banda.network.domain.ReservoirSetting
import org.apache.spark.ml.linalg.SQLDataTypes
import org.apache.spark.ml.{Estimator, Pipeline, PipelineModel, Transformer}
import org.apache.spark.ml.param.{Param, ParamMap}
import org.apache.spark.ml.util.{DefaultParamsWritable, Identifiable}
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.{DataFrame, Dataset}
import org.apache.spark.sql.types._
import services.SparkUtil

private class RCStatesWindow(override val uid: String, reservoirRunnableFactory: ReservoirRunnableFactory) extends Transformer with DefaultParamsWritable {

  def this(reservoirRunnableFactory: ReservoirRunnableFactory) = this(Identifiable.randomUID("rc_states_window"), reservoirRunnableFactory)

  protected final val setting: Param[ReservoirSetting] = new Param[ReservoirSetting](this, "setting", "Reservoir Setting")
  protected final val inputCol: Param[String] = new Param[String](this, "inputCol", "input column name")
  protected final val orderCol: Param[String] = new Param[String](this, "orderCol", "order column name")
  protected final val outputCol: Param[String] = new Param[String](this, "outputCol", "output column name")

  def setSetting(value: ReservoirSetting): this.type = set(setting, value)
  def setInputCol(value: String): this.type = set(inputCol, value)
  def setOrderCol(value: String): this.type = set(orderCol, value)
  def setOutputCol(value: String): this.type = set(outputCol, value)

  // create RC network runnable with input nodes and reservoir nodes
  protected lazy val (networkRunnable, inputNodes, reservoirNodes) = {
    println("Creating RC networkRunnable")
    reservoirRunnableFactory($(setting))
  }

  override def transform(dataset: Dataset[_]): DataFrame = {
    // create a network state agg fun
    val rcAggFun = new NetworkStateVectorAgg(networkRunnable, inputNodes, reservoirNodes)

    // data frame with a sliding window with the RC network state agg function
    dataset.withColumn($(outputCol), rcAggFun(dataset($(inputCol))).over(Window.orderBy($(orderCol))))
  }

  override def copy(extra: ParamMap): RCStatesWindow = defaultCopy(extra)

  override def transformSchema(schema: StructType): StructType = {
    val existingFields = schema.fields

    require(!existingFields.exists(_.name == $(outputCol)),
      s"Output column ${$(outputCol)} already exists.")

    schema.add(StructField($(outputCol), SQLDataTypes.VectorType, true))
  }
}

class RCStatesWindowFactory @Inject() (reservoirRunnableFactory: ReservoirRunnableFactory) {

  def apply(
    setting: ReservoirSetting,
    inputCol: String,
    orderCol: String,
    outputCol: String
  ): Transformer =
    new RCStatesWindow(reservoirRunnableFactory).setSetting(setting).setInputCol(inputCol).setOrderCol(orderCol).setOutputCol(outputCol)

  def applyInPlace(
    setting: ReservoirSetting,
    inputOutputCol: String,
    orderCol: String
  ): Estimator[PipelineModel] =
    SparkUtil.transformInPlace(
      apply(setting, inputOutputCol, orderCol, _),
      inputOutputCol
    )
}