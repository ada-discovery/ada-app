package services.ml.transformers

import org.apache.spark.ml.linalg._
import org.apache.spark.sql.Row
import org.apache.spark.sql.expressions.{MutableAggregationBuffer, UserDefinedAggregateFunction}
import org.apache.spark.sql.types._
import scala.collection.mutable.WrappedArray

object VectorSum extends VectorAgg {
  override protected def initValue = 0d

  override protected def updateBuffer(
    buff: WrappedArray[Double],
    i: Int,
    value: Double
  ) =
    buff(i) += value
}

object VectorMax extends VectorAgg {
  override protected def initValue = Double.MinValue

  override protected def updateBuffer(
    buff: WrappedArray[Double],
    i: Int,
    value: Double
  ) =
    buff(i) = math.max(buff(i), value)
}

object VectorMin extends VectorAgg {
  override protected def initValue = Double.MaxValue

  override protected def updateBuffer(
    buff: WrappedArray[Double],
    i: Int,
    value: Double
  ) =
    buff(i) = math.min(buff(i), value)
}

protected abstract class VectorAgg extends UserDefinedAggregateFunction {

  protected def initValue: Double

  protected def updateBuffer(
    buff: WrappedArray[Double],
    i: Int,
    value: Double
  ): Unit

  override def inputSchema = StructType(StructField("vec", SQLDataTypes.VectorType) :: Nil)

  override def bufferSchema = StructType(StructField("agg", ArrayType(DoubleType)) :: Nil)

  override def dataType = SQLDataTypes.VectorType

  override def deterministic: Boolean = true

  def initialize(buffer: MutableAggregationBuffer) =
    buffer.update(0, Array.empty[DoubleType])

  private def newZeroArray(size: Int): WrappedArray[Double] =
    WrappedArray.make(Array.fill(size)(initValue))

  def update(buffer: MutableAggregationBuffer, input: Row) = {
    if (!input.isNullAt(0)) {
      val vector = input.getAs[Vector](0)
      val buff = buffer.getAs[WrappedArray[Double]](0)
      val initBuff = if (buff.isEmpty) newZeroArray(vector.size) else buff

      vector match {
        case DenseVector(values) =>
          values.zipWithIndex.foreach { case (value, i) => updateBuffer(initBuff, i, value) }

        case SparseVector(_, indices, values) =>
          values.zip(indices).foreach { case (value, i) => updateBuffer(initBuff, i, value) }
      }

      buffer.update(0, initBuff)
    }
  }

  def merge(buffer1: MutableAggregationBuffer, buffer2: Row) = {
    val buff1 = buffer1.getAs[WrappedArray[Double]](0)
    val buff2 = buffer2.getAs[WrappedArray[Double]](0)

    val initBuff1 = if (buff1.isEmpty && buff2.nonEmpty) newZeroArray(buff2.size) else buff1

    for ((value, i) <- buff2.zipWithIndex)
      updateBuffer(initBuff1, i, value)

    buffer1.update(0, initBuff1)
  }

  def evaluate(buffer: Row) =  Vectors.dense(buffer.getAs[WrappedArray[Double]](0).toArray)
}